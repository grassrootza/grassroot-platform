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
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import java.io.File;
import java.io.FileInputStream;


@Slf4j
@Controller
@RequestMapping("/api/user")
public class UserController {


    private final MediaFileBroker mediaFileBroker;

    private final StorageBroker storageBroker;

    @Value("${grassroot.media.user-photo.folder}")
    private String userProfileImagesFolder;

    public UserController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker) {
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
    }

    @ApiOperation(value = "Store a media file, and get the server key back",
            notes = "Message will declare if the file already existed, by having already_exists or uploaded, and" +
                    "the 'data' field contains a string that will have the UID of the file record on the server (storedFileUid)")
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


}
