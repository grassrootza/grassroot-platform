package za.org.grassroot.webapp.controller.rest.user;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.authentication.CreateJwtTokenRequest;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.integration.authentication.JwtType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;
import za.org.grassroot.services.geo.AddressBroker;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.controller.rest.file.MediaUploadResult;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.AuthorizedUserDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.time.Instant;
import java.util.Collections;


@Slf4j @RestController @Grassroot2RestController
@RequestMapping("/v2/api/user/profile") @Api("/v2/api/user/profile")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class UserController extends BaseRestController {

    private final MediaFileBroker mediaFileBroker;
    private final UserManagementService userService;
    private final PasswordTokenService passwordService;
    private final JwtService jwtService;
    private final AddressBroker addressBroker;
    private final UserLocationLogRepository userLocationLogRepository;

    @Value("${grassroot.media.user-photo.folder:user-profile-images-staging}")
    private String userProfileImagesFolder;

    public UserController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker,
                          UserManagementService userService, JwtService jwtService,
                          PasswordTokenService passwordService, AddressBroker addressBroker,
                          UserLocationLogRepository userLocationLogRepository) {
        super(jwtService, userService);
        this.mediaFileBroker = mediaFileBroker;
        this.userService = userService;
        this.passwordService = passwordService;
        this.jwtService = jwtService;
        this.addressBroker = addressBroker;
        this.userLocationLogRepository = userLocationLogRepository;
    }

    @ApiOperation(value = "Store a users profile photo, and get the server key back")
    @RequestMapping(value = "/image/change", method = RequestMethod.POST)
    public ResponseEntity uploadProfileImage(@RequestBody MultipartFile photo, HttpServletRequest request) {
        String userUid = getUserIdFromRequest(request);
        String imageKey = userProfileImagesFolder + "/" + userUid;

        log.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, MediaFunction.USER_PROFILE_IMAGE);

        String storedFileUid = mediaFileBroker.storeFile(photo, MediaFunction.USER_PROFILE_IMAGE, null, imageKey, photo.getName());
        userService.updateHasImage(userUid, true);
        MediaUploadResult result = MediaUploadResult.builder().mediaRecordUid(storedFileUid).build();
        return ResponseEntity.ok(result);
    }

    @ApiOperation(value = "Updates user profile data",
            notes = "Update result message is returned as a string in the 'data' property")
    @RequestMapping(value = "/data/update", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateProfileData(@RequestParam String name,
                                                             @RequestParam String phone,
                                                             @RequestParam String email,
                                                             @RequestParam String language,
                                                             @RequestParam(required = false) Province province,
                                                             @RequestParam(required = false) String validationOtp,
                                                             HttpServletRequest request) {

        String jwtToken = getJwtTokenFromRequest(request);
        User user = getUserFromToken(jwtToken);

        try {
            log.info("updating user info, name = {}, phone = {}, email = {}, language = {}, province = {}, " +
                    "validation = {}", name, phone, email, language, province, validationOtp);

            boolean updateCompleted = userService.updateUser(user.getUid(), name, phone, email, province,
                user.getAlertPreference(), user.getLocale(), validationOtp);

            if (!updateCompleted) {
                passwordService.triggerOtp(user);
                return RestUtil.messageOkayResponse(RestMessage.OTP_REQUIRED);
            }

            user.setDisplayName(name);
            user.setPhoneNumber(phone);
            user.setEmailAddress(email);
            user.setProvince(province);
            user.setLanguageCode(language);

            AuthorizedUserDTO response = new AuthorizedUserDTO(user, jwtToken);
            return RestUtil.okayResponseWithData(RestMessage.UPDATED, response);
        } catch (InvalidOtpException e) {
            return RestUtil.errorResponse(RestMessage.INVALID_OTP);
        }
    }

    @ApiOperation(value = "Update user password, if passed old password")
    @RequestMapping(value = "/password/update", method = RequestMethod.POST)
    public ResponseEntity changeUserPassword(HttpServletRequest request,
                                                       @RequestParam String oldPassword,
                                                       @RequestParam String newPassword,
                                                       @RequestParam String confirmNewPwd,
                                                       @RequestParam UserInterfaceType callingInterface) {
        // validated on client side but doesn't hurt to double check (and prevent misbehaviour maybe)
        if (!newPassword.equals(confirmNewPwd)) {
            return RestUtil.errorResponse(RestMessage.INVALID_PASSWORD);
        }
        try {
            passwordService.changeUserPassword(getUserIdFromRequest(request), oldPassword, newPassword, callingInterface);
            return ResponseEntity.ok().build();
        } catch (UsernamePasswordLoginFailedException e) {
            return RestUtil.errorResponse(RestMessage.INVALID_PASSWORD);
        }
    }

    @ApiOperation(value = "Initiate deleting user's account, by generating an OTP")
    @RequestMapping(value = "/delete/initiate", method = RequestMethod.POST)
    public ResponseEntity initiateDeleteProfile(HttpServletRequest request) {
        final String userUid = getUserIdFromRequest(request);
        User user = userService.load(userUid);
        passwordService.triggerOtp(user);
        return RestUtil.messageOkayResponse(RestMessage.OTP_REQUIRED);
    }

    @ApiOperation(value = "Complete deleting user's account, validated by OTP")
    @RequestMapping(value = "/delete/confirm", method = RequestMethod.POST)
    public ResponseEntity completeProfileDelete(HttpServletRequest request, @RequestParam String otp) {
        final String userUid = getUserIdFromRequest(request);
        userService.deleteUser(userUid, otp);
        addressBroker.removeAddress(userUid);
        return ResponseEntity.ok(RestMessage.USER_DELETED);
    }

    @ApiOperation(value = "Get an access token for joining groups, to use in eg 3rd party apps")
    @RequestMapping(value = "/token/obtain", method = RequestMethod.GET)
    public ResponseEntity obtainTokenForApi(HttpServletRequest request) {
        final String userId = getUserIdFromRequest(request);
        CreateJwtTokenRequest jwtRequest = new CreateJwtTokenRequest(JwtType.API_CLIENT,
                userId, Collections.singleton(Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER.getName()));
        return ResponseEntity.ok(jwtService.createJwt(jwtRequest));
    }

    @RequestMapping(value = "/user/location",method = RequestMethod.POST)
    public ResponseEntity createUserLocationLog(@RequestParam double lat,
                                                @RequestParam double lon,
                                                HttpServletRequest request){
        log.info("Recieved coodinates, lat = {} , long = {}",lat,lon);

        String userUid = getUserIdFromRequest(request);
        GeoLocation geoLocation = new GeoLocation(lat,lon);

        userLocationLogRepository.save(new UserLocationLog(Instant.now(),userUid,geoLocation, LocationSource.LOGGED_APPROX));
        return ResponseEntity.ok(RestMessage.LOCATION_RECORDED);
    }
}
