package za.org.grassroot.webapp.controller.webapp;

import com.amazonaws.util.IOUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.domain.media.MediaFunction;
import za.org.grassroot.integration.MediaFileBroker;
import za.org.grassroot.integration.PdfGeneratingService;
import za.org.grassroot.integration.storage.ImageType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.util.ImageUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.*;
import java.util.Base64;
import java.util.Locale;

/**
 * Created by paballo on 2016/07/06.
 */
@Controller @Slf4j @Grassroot2RestController
@RequestMapping("/image/")
public class ImageController {

    @Value("${grassroot.media.user-photo.folder:user-profile-images-staging}")
    private String userProfileImagesFolder;

    private final GroupImageBroker groupImageBroker;
    private final StorageBroker storageBroker;
    private final MediaFileBroker mediaFileBroker;
    private final PdfGeneratingService generatingService;

    @Autowired
    public ImageController(GroupImageBroker groupImageBroker, StorageBroker storageBroker, MediaFileBroker mediaFileBroker, PdfGeneratingService generatingService) {
        this.groupImageBroker = groupImageBroker;
        this.storageBroker = storageBroker;
        this.mediaFileBroker = mediaFileBroker;
        this.generatingService = generatingService;
    }

    @RequestMapping(value = "/grassroot-task-images/{imageKey}", method = RequestMethod.GET)
    public String viewTaskImage(@PathVariable String imageKey, Model model) {
        byte[] imageByteArray = storageBroker.fetchTaskImage(imageKey, ImageType.FULL_SIZE);
        String image = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(imageByteArray);
        model.addAttribute("image", image);
        return "image_view";
    }

    /*
    Below is likely redundant, but leaving it in place for now in case of legacy
     */

    @RequestMapping(value = "get", method = RequestMethod.GET)
    public ResponseEntity<?> getImage(@RequestParam String imageId, HttpServletRequest request) throws IOException {

        String scheme = request.getScheme();
        String serverName = request.getServerName();
        int serverPort = request.getServerPort();
        StringBuilder builder = new StringBuilder();
        builder.append(scheme).append("://").append(serverName);
        if (serverPort != 80 && serverPort != 443) {
            builder.append(":").append(serverPort);
        }

        String imageUrl = builder.append(ImageUtil.getRelativePath())
                .append(imageId).toString();

        HttpHeaders headers = new HttpHeaders();
        Group group = groupImageBroker.getGroupByImageUrl(imageUrl);

        if(group != null) {
            byte[] image = group.getImage();
            String mimeType = ImageUtil.getMimeType(imageId);
            headers.setContentType(MediaType.parseMediaType(mimeType));

            return ResponseEntity.ok().lastModified(24000).headers(headers).body(image);
        }

        return ResponseEntity.notFound().build();
    }

    @RequestMapping(value = "processed", method = RequestMethod.POST)
    public ResponseEntity<String> processImage() {
        return ResponseEntity.ok("done");
    }

    @RequestMapping(value = "/flyer/group/{groupUid}", method = RequestMethod.GET, params = "typeOfFile=PDF", produces = MediaType.APPLICATION_PDF_VALUE)
    @ResponseBody //"application/pdf",
    public FileSystemResource genPdf(@PathVariable String groupUid,
                                     @RequestParam boolean color,
                                     @RequestParam Locale language,
                                     @RequestParam String typeOfFile) {
        return generateFlyer(groupUid, color, language, typeOfFile);
    }

    @RequestMapping(value = "/flyer/group/{groupUid}", method = RequestMethod.GET, params = "typeOfFile=JPEG", produces = MediaType.IMAGE_JPEG_VALUE)
    @ResponseBody
    public FileSystemResource genImage(@PathVariable String groupUid,
                                       @RequestParam boolean color,
                                       @RequestParam Locale language,
                                       @RequestParam String typeOfFile) {
        return generateFlyer(groupUid, color, language, typeOfFile);
    }

    private FileSystemResource generateFlyer(String groupUid, boolean color, Locale language, String typeOfFile) {
        try {
            return new FileSystemResource(generatingService.generateGroupFlyer(groupUid, color, language, typeOfFile));
        } catch (FileNotFoundException e) {
            log.error("Could not generate flyer!", e);
            return null;
        }
    }

    @RequestMapping(value = "/user/{userUid}", method = RequestMethod.GET)
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
            return (ResponseEntity<byte[]>) new ResponseEntity(data, headers, HttpStatus.OK);
        } catch (Exception e) {
            log.error("Failed to fetch user profile image for user with uid: " + userUid, e);
            return new ResponseEntity(null, HttpStatus.INTERNAL_SERVER_ERROR);
        }
    }

}