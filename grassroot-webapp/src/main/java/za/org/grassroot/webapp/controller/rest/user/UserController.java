package za.org.grassroot.webapp.controller.rest.user;

import com.amazonaws.util.IOUtils;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.VerificationTokenCode;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.messaging.GrassrootEmail;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.exception.InvalidOtpException;
import za.org.grassroot.services.exception.UsernamePasswordLoginFailedException;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.AuthorizedUserDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.Locale;


@Slf4j
@Controller
@RequestMapping("/api/user/profile")
public class UserController extends BaseRestController {

    private final MediaFileBroker mediaFileBroker;
    private final StorageBroker storageBroker;
    private final UserManagementService userService;
    private final PasswordTokenService passwordService;
    private final MessagingServiceBroker messagingBroker;
    private final MessageSourceAccessor messageSource;


    @Value("${grassroot.media.user-photo.folder:user-profile-images-staging}")
    private String userProfileImagesFolder;

    public UserController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker,
                          UserManagementService userService, JwtService jwtService,
                          PasswordTokenService passwordService, MessagingServiceBroker messagingBroker,
                          @Qualifier("messageSourceAccessor") MessageSourceAccessor messageSource) {
        super(jwtService, userService);
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
        this.userService = userService;
        this.passwordService = passwordService;
        this.messagingBroker = messagingBroker;
        this.messageSource = messageSource;
    }

    @ApiOperation(value = "Store a users profile photo, and get the server key back",
            notes = " 'data' field contains a string that will have the UID of the file record on the server (storedFileUid)")
    @RequestMapping(value = "/image/change", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadProfileImage(@RequestParam MultipartFile photo, HttpServletRequest request) {


        String userUid = getUserIdFromRequest(request);
        String imageKey = userProfileImagesFolder + "/" + userUid;
        MediaFunction mediaFunction = MediaFunction.USER_PROFILE_IMAGE;
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        log.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);

        String storedFileUid = mediaFileBroker.storeFile(photo, mediaFunction, null, imageKey);
        return RestUtil.okayResponseWithData(RestMessage.UPLOADED, storedFileUid);
    }


    @RequestMapping(value = "/image/view/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> viewProfileImage(@PathVariable String userUid) {

        try {
            String imageKey = userProfileImagesFolder + "/" + userUid;
            MediaFunction mediaFunction = MediaFunction.USER_PROFILE_IMAGE;

            MediaFileRecord mediaFileRecord = mediaFileBroker.load(mediaFunction, imageKey);
            byte[] data;
            if (mediaFileRecord != null) {
                File imageFile = storageBroker.fetchFileFromRecord(mediaFileRecord);
                data = IOUtils.toByteArray(new FileInputStream(imageFile));
            } else {
                InputStream in = getClass().getResourceAsStream("/static/images/user.png");
                data = IOUtils.toByteArray(in);
            }
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            ResponseEntity<byte[]> response = new ResponseEntity(data, headers, HttpStatus.OK);
            return response;
        } catch (Exception e) {
            log.error("Failed to fetch user profile image for user with uid: " + userUid, e);
            return new ResponseEntity(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
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
                user.getAlertPreference(), new Locale(user.getLanguageCode()), validationOtp);

            if (!updateCompleted) {
                sendOtp(user);
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

    private void sendOtp(User user) {
        final String message = otpMessage(user.getUsername(), user.getLocale());
        try {
            if (user.hasPhoneNumber()) {
                messagingBroker.sendPrioritySMS(message, user.getPhoneNumber()); // _not_ new one, obviously
            } else {
                messagingBroker.sendEmail(Collections.singletonList(user.getEmailAddress()),
                        new GrassrootEmail.EmailBuilder().subject("Your Grassroot verification").content(message).build());
            }
        } catch (Exception e) {
            log.error("No message broker around, couldn't send {}", message);
        }
    }

    private String otpMessage(String username, Locale locale) {
        final VerificationTokenCode otp = passwordService.generateShortLivedOTP(username);
        return messageSource.getMessage("web.user.profile.token.message", new String[] {otp.getCode()}, locale);
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



}
