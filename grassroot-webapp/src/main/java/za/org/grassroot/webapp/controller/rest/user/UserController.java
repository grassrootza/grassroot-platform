package za.org.grassroot.webapp.controller.rest.user;

import com.amazonaws.util.IOUtils;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
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
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.http.AuthorizationHeader;
import za.org.grassroot.webapp.model.rest.AndroidAuthToken;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;


@Slf4j
@Controller
@RequestMapping("/api/user")
public class UserController {


    private final MediaFileBroker mediaFileBroker;

    private final StorageBroker storageBroker;

    private final UserManagementService userService;

    private final JwtService jwtService;


    @Value("${grassroot.media.user-photo.folder}")
    private String userProfileImagesFolder;

    public UserController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker,
                          UserManagementService userService, JwtService jwtService) {
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
        this.userService = userService;
        this.jwtService = jwtService;
    }

    @ApiOperation(value = "Store a users profile photo, and get the server key back",
            notes = " 'data' field contains a string that will have the UID of the file record on the server (storedFileUid)")
    @RequestMapping(value = "/change-profile-img/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadProfileImage(@PathVariable String userUid,
                                                              @RequestParam MultipartFile photo) {

        String imageKey = userProfileImagesFolder + "/" + userUid;
        MediaFunction mediaFunction = MediaFunction.USER_PROFILE_IMAGE;
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        log.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);

        String storedFileUid = mediaFileBroker.storeFile(photo, mediaFunction, null, imageKey);
        return RestUtil.okayResponseWithData(RestMessage.UPLOADED, storedFileUid);
    }



    @RequestMapping(value = "/profile-image/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> viewProfileImage(@PathVariable String userUid) {

        try {

            String imageKey = userProfileImagesFolder + "/" + userUid;
            MediaFunction mediaFunction = MediaFunction.USER_PROFILE_IMAGE;

            MediaFileRecord mediaFileRecord = mediaFileBroker.load(mediaFunction, imageKey);
            File imageFile = storageBroker.fetchFileFromRecord(mediaFileRecord);
            byte[] data = IOUtils.toByteArray(new FileInputStream(imageFile));
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
    @RequestMapping(value = "/update-profile-data", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateProfileData(@RequestParam String displayName,
                                                             @RequestParam String phoneNumber,
                                                             @RequestParam String email,
                                                             HttpServletRequest request) {

        String jwtToken = null;
        String userUid = null;
        AuthorizationHeader authorizationHeader = new AuthorizationHeader(request);
        if (authorizationHeader.hasBearerToken()) {
            jwtToken = authorizationHeader.getBearerToken();
            userUid = jwtService.getUserIdFromJwtToken(jwtToken);
        }

        if (userUid == null)
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN);

        User user = userService.load(userUid);

        if (user == null)
            return RestUtil.errorResponse(HttpStatus.UNAUTHORIZED, RestMessage.INVALID_TOKEN);

        AndroidAuthToken response = new AndroidAuthToken(user, jwtToken);
        return RestUtil.okayResponseWithData(RestMessage.LOGIN_SUCCESS, response);
    }


}
