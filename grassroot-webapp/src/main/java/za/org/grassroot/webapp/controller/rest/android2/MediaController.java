package za.org.grassroot.webapp.controller.rest.android2;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.storage.StorageBroker;

@RestController
@RequestMapping(value = "/api/mobile/media")
public class MediaController {

    private static final Logger logger = LoggerFactory.getLogger(MediaController.class);

    private final StorageBroker storageBroker;

    @Autowired
    public MediaController(StorageBroker storageBroker) {
        this.storageBroker = storageBroker;
    }

    @RequestMapping(value = "/store")
    public ResponseEntity<String> storeMediaFile(@RequestParam String userUid,
                                                 @RequestParam String imageKey,
                                                 @RequestParam MediaFunction mediaFunction,
                                                 @RequestParam("image") MultipartFile file) {
        // store the media, depending on its function (if task image stick in there so analysis etc is triggered)
        return ResponseEntity.ok("URL goes here");
    }

}
