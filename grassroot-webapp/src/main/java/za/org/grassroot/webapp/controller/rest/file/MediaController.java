package za.org.grassroot.webapp.controller.rest.file;

import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;

@RestController @Grassroot2RestController
@Api("/v2/api/media") @Slf4j
@RequestMapping(value = "/v2/api/media")
@PreAuthorize("hasRole('ROLE_FULL_USER')")
public class MediaController extends BaseRestController {

    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public MediaController(JwtService jwtService, UserManagementService userManagementService, MediaFileBroker mediaFileBroker) {
        super(jwtService, userManagementService);
        this.mediaFileBroker = mediaFileBroker;
        log.info("CONTROLLER :: {}", mediaFileBroker);
    }

    // respective libraries on other end handle file uploads differently, hence duplication in here

    @ApiOperation(value = "Store a media file, and get the server key back (Android/Retrofit2 version",
            notes = "Message will declare if the file already existed, by having already_exists or uploaded, and" +
                    "the 'data' field contains a string that will have the UID of the file record on the server (storedFileUid)")
    @RequestMapping(value = "/store/param/{mediaFunction}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> storeMediaFileAndroid(@PathVariable MediaFunction mediaFunction,
                                                          @RequestParam(required = false) String imageKey,
                                                          @RequestParam(required = false) String mimeType,
                                                          @RequestParam MultipartFile file) {
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        log.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);
        return storeMediaFile(mediaFunction, imageKey, mimeType, file);
    }

    @ApiOperation(value = "Store a media file, and get the server key back",
            notes = "The obove method 'storeMedia'could not work on web, " +
                    "@RequestParam MultipartFile is required as RequestBody for web, hence duplicating")
    @RequestMapping(value = "/store/body/{mediaFunction}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> storeMediaFileWeb(@PathVariable MediaFunction mediaFunction,
                                                             @RequestParam(required = false) String imageKey,
                                                             @RequestParam(required = false) String mimeType,
                                                             @RequestBody MultipartFile file) {
        log.info("Media function: {}, File Name: {}", mediaFunction, file.getOriginalFilename());
        return storeMediaFile(mediaFunction, imageKey, mimeType, file);
    }

    private ResponseEntity<ResponseWrapper> storeMediaFile(@PathVariable MediaFunction mediaFunction,
                                                          @RequestParam(required = false) String imageKey,
                                                          @RequestParam(required = false) String mimeType,
                                                          @RequestParam MultipartFile file) {
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        log.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);
        boolean duplicate = mediaFileBroker.doesFileExist(mediaFunction, imageKey);
        String storedFileUid = duplicate ?
                mediaFileBroker.load(mediaFunction, imageKey).getUid() :
                mediaFileBroker.storeFile(file, mediaFunction, mimeType, imageKey, file.getOriginalFilename());
        return RestUtil.okayResponseWithData(duplicate ? RestMessage.ALREADY_EXISTS : RestMessage.UPLOADED, storedFileUid);
    }

    @RequestMapping(value = "/store/record", method = RequestMethod.POST)
    public ResponseEntity recordMediaFile(HttpServletRequest request,
                                           @RequestParam String bucket,
                                           @RequestParam String imageKey,
                                           @RequestParam String mimeType) {
        log.info("Recording a media file, with image key {}, and bucket {}", imageKey, bucket);
        String storedFileUid = mediaFileBroker.recordFile(getUserIdFromRequest(request), bucket, mimeType, imageKey, null);
        return ResponseEntity.ok(storedFileUid);
    }


}
