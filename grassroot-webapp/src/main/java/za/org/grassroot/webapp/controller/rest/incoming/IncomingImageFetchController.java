package za.org.grassroot.webapp.controller.rest.incoming;

import com.amazonaws.util.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

@Slf4j
@RestController @Grassroot2RestController
@RequestMapping("/image")
public class IncomingImageFetchController {

    private final MediaFileBroker mediaFileBroker;
    private final StorageBroker storageBroker;

    @Autowired
    public IncomingImageFetchController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker) {
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
    }

    @RequestMapping("/broadcast/{mediaFileKey}")
    public ResponseEntity<byte[]> getBroadcastImage(@PathVariable String mediaFileKey)
            throws IOException {
        MediaFileRecord record = mediaFileBroker.load(MediaFunction.BROADCAST_IMAGE, mediaFileKey);
        File imageFile = storageBroker.fetchFileFromRecord(record);
        byte[] data = IOUtils.toByteArray(new FileInputStream(imageFile));
        HttpHeaders headers = new HttpHeaders();
        try {
            headers.setContentType(MediaType.parseMediaType(record.getMimeType()));
        } catch (InvalidMediaTypeException e) {
            log.info("error processing mime type, record has: {}", record.getMimeType());
            log.error("couldn't set MIME heading ...", e);
        }
        String filename = "image-" + mediaFileKey + "." + record.getMimeType();
        log.info("file name : ", filename);
        headers.setContentDispositionFormData(filename, filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @ExceptionHandler(IOException.class)
    public ResponseEntity noImageResponse(IOException e) {
        log.error("IO Exception in image fetching: ", e);
        return new ResponseEntity(null, HttpStatus.INTERNAL_SERVER_ERROR);
    }

}
