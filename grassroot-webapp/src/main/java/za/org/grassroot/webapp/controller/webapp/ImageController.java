package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.services.GroupBroker;

import java.io.IOException;

/**
 * Created by paballo on 2016/07/06.
 */
@Controller
@RequestMapping("/image/")
public class ImageController {

    private Logger log = LoggerFactory.getLogger(ImageController.class);

    @Autowired
    private GroupBroker groupBroker;

    @RequestMapping(value = "/get",method = RequestMethod.GET)
    public ResponseEntity<byte[]> getImage(@RequestParam String imageId) throws IOException {

        log.info("Image id " + imageId);

        String[] parts = imageId.split("\\.");
        String groupUid = parts[0];
        Group group = groupBroker.load(groupUid);

        byte[] b = group.getImage();
        String contentType = group.getImageType();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(contentType));

        return new ResponseEntity<>(b, headers, HttpStatus.OK);

    }

    //for testing purposes
    @RequestMapping(method = RequestMethod.GET, value = "/upload")
    public String provideUploadInfo(Model model) throws IOException {
        return "upload";
    }

}
