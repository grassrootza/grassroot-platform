package za.org.grassroot.webapp.controller.rest.incoming;

import com.amazonaws.util.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.util.ImageUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;

@Slf4j
@RestController @Grassroot2RestController
@RequestMapping(value = { "/image", "/v2/api/image" })
public class IncomingImageFetchController {

    @Value("${grassroot.media.user-photo.folder:user-profile-images-staging}")
    private String userProfileImagesFolder;

    private final MediaFileBroker mediaFileBroker;
    private final StorageBroker storageBroker;
    private final GroupImageBroker groupImageBroker;

    private PdfGeneratingService pdfGeneratingService; // may decide to turn off in future deployments

    @Autowired
    public IncomingImageFetchController(MediaFileBroker mediaFileBroker, StorageBroker storageBroker, GroupImageBroker groupImageBroker) {
        this.mediaFileBroker = mediaFileBroker;
        this.storageBroker = storageBroker;
        this.groupImageBroker = groupImageBroker;
    }

    @Autowired
    public void setPdfGeneratingService(PdfGeneratingService pdfGeneratingService) {
        this.pdfGeneratingService = pdfGeneratingService;
    }

    @RequestMapping(value = "/broadcast/{mediaFileKey}", method = RequestMethod.GET)
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
            log.error("couldn't set MIME heading ... {}", e.getMessage());
        }
        String filename = "image-" + mediaFileKey + "." + record.getMimeType();
        log.debug("file name : {}", filename);
        headers.setContentDispositionFormData(filename, filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    @RequestMapping(value = "/user/{userUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> viewProfileImage(@PathVariable String userUid) throws IOException {
        MediaFileRecord userImgRecord = mediaFileBroker.load(MediaFunction.USER_PROFILE_IMAGE,
                userProfileImagesFolder + "/" + userUid);
        log.info("Fetched record: {}", userImgRecord);
        return convertRecordToResponse(userImgRecord);
    }

    @RequestMapping(value = "/group/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getGroupProfileImage(@PathVariable String groupUid) {
        Optional<Group> group = groupImageBroker.getGroupByUidOrImageUrl(groupUid);
        return group.map(this::convertGroupToImage).orElse(ResponseEntity.notFound().build());
    }

    @RequestMapping(value = "/flyer/{groupUid}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> getGroupFlyerImage(@PathVariable String groupUid,
                                                     @RequestParam String typeOfFile,
                                                     @RequestParam boolean color,
                                                     @RequestParam Locale language) throws IOException {
        File image = pdfGeneratingService.generateGroupFlyer(groupUid, color, language, typeOfFile);
        return convertFileToResponse(image, typeOfFile.equals("JPEG") ? MediaType.IMAGE_JPEG : MediaType.APPLICATION_PDF);
    }

    @RequestMapping(value = "/get", params = {"imageId"})
    public ResponseEntity<byte[]> getGroupImageLegacy(@RequestParam String imageId, HttpServletRequest request) {
        log.info("Old school fetching group image, image Id: {}", imageId);
        Optional<Group> group = groupImageBroker.getGroupByUidOrImageUrl(legacyConvertImageKey(imageId, request));
        log.info("Found a group? : {}", group);
        return group.map(this::convertGroupToImage).orElse(ResponseEntity.notFound().build());
    }

    private ResponseEntity<byte[]> convertGroupToImage(Group group) {
        byte[] image = group.getImage();
        String mimeType = ImageUtil.getMimeType(group.getImageUrl());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mimeType));
        return ResponseEntity.ok().lastModified(24000).headers(headers).body(image);
    }

    private String legacyConvertImageKey(String imageId, HttpServletRequest request) {
        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) {
            builder.append(":").append(serverPort);
        }

        return builder.append(ImageUtil.getRelativePath()).append(imageId).toString();
    }

    @RequestMapping(value = "/{mediaFunction}/{imageKey}", method = RequestMethod.GET)
    public ResponseEntity<byte[]> fetchImage(@PathVariable MediaFunction mediaFunction,
                                             @PathVariable String imageKey) throws IOException {
        MediaFileRecord record = mediaFileBroker.load(mediaFunction, imageKey);
        log.info("record retrieved: {}", record);
        return convertRecordToResponse(record);
    }

    private ResponseEntity<byte[]> convertRecordToResponse(MediaFileRecord record) throws IOException {
        File imageFile = storageBroker.fetchFileFromRecord(record);
        byte[] data = IOUtils.toByteArray(new FileInputStream(imageFile));
        HttpHeaders headers = new HttpHeaders();
        try {
            headers.setContentType(MediaType.parseMediaType(record.getMimeType()));
        } catch (InvalidMediaTypeException e) {
            log.info("error processing mime type, record has: {}", record.getMimeType());
            log.error("couldn't set MIME heading ...", e);
        }
        String filename = "image-" + record.getKey() + "." + record.getMimeType();
        log.debug("file name : {}", filename);
        headers.setContentDispositionFormData(filename, filename);
        headers.setCacheControl("must-revalidate, post-check=0, pre-check=0");
        return new ResponseEntity<>(data, headers, HttpStatus.OK);
    }

    private ResponseEntity<byte[]> convertFileToResponse(File imageFile, MediaType mediaType) throws IOException {
        byte[] data = IOUtils.toByteArray(new FileInputStream(imageFile));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(mediaType);
        String filename = imageFile.getName();
        log.debug("file name : {}", filename);
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
