package za.org.grassroot.webapp.controller.webapp;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.integration.storage.ImageType;
import za.org.grassroot.integration.storage.StorageBroker;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.webapp.util.ImageUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Base64;

/**
 * Created by paballo on 2016/07/06.
 */
@Controller
@RequestMapping("/image/")
public class ImageController {

    private final GroupImageBroker groupImageBroker;
    private final StorageBroker storageBroker;

    @Autowired
    public ImageController(GroupImageBroker groupImageBroker, TaskImageBroker taskImageBroker, StorageBroker storageBroker) {
        this.groupImageBroker = groupImageBroker;
        this.storageBroker = storageBroker;
    }

    @RequestMapping(value = "/grassroot-task-images/{imageKey}", method = RequestMethod.GET)
    public String viewTaskImage(@PathVariable String imageKey, Model model) {
        byte[] imageByteArray = storageBroker.fetchImage(imageKey, ImageType.FULL_SIZE);
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



}