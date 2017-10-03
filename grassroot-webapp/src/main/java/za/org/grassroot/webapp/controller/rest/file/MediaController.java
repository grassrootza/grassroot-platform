package za.org.grassroot.webapp.controller.rest.file;

import io.swagger.annotations.Api;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@Api("/api/media")
@RequestMapping(value = "/api/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final MediaFileBroker mediaFileBroker;

    @Autowired
    public MediaController(MediaFileBroker mediaFileBroker) {
        this.mediaFileBroker = mediaFileBroker;
    }

    @RequestMapping(value = "/store/{userUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> storeMediaFile(@PathVariable String userUid,
                                                          @RequestParam String imageKey,
                                                          @RequestParam MediaFunction mediaFunction,
                                                          @RequestParam String mimeType,
                                                          @RequestParam MultipartFile file) {
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        logger.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);
        boolean duplicate = mediaFileBroker.doesFileExist(mediaFunction, imageKey);
        String storedFileUid = duplicate ?
                mediaFileBroker.load(mediaFunction, imageKey).getUid() :
                mediaFileBroker.storeFile(file, mediaFunction, null, imageKey);
        return RestUtil.okayResponseWithData(duplicate ? RestMessage.ALREADY_EXISTS : RestMessage.UPLOADED, storedFileUid);
    }

}
