package za.org.grassroot.webapp.controller.rest.android2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

@RestController
@RequestMapping(value = "/api/mobile/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final MediaFileBroker mediaFileBroker;
    private final StorageBroker storageBroker;

    @Autowired
    public MediaController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker) {
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
    }

    @RequestMapping(value = "/store/{userUid}")
    public ResponseEntity<ResponseWrapper> storeMediaFile(@PathVariable String userUid,
                                                          @RequestParam String imageKey,
                                                          @RequestParam MediaFunction mediaFunction,
                                                          @RequestParam String mimeType,
                                                          @RequestParam MultipartFile file) {
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        logger.info("storing a media file, with imageKey = {}, and mediaFunction = {}", imageKey, mediaFunction);
        String storedFileUid = mediaFileBroker.storeFile(file, mediaFunction, null, imageKey);
        return RestUtil.okayResponseWithData(RestMessage.UPLOADED, storedFileUid);
    }

}
