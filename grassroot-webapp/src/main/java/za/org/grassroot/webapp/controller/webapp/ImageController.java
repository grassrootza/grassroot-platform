package za.org.grassroot.webapp.controller.webapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.webapp.util.ImageUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

/**
 * Created by paballo on 2016/07/06.
 */
@Controller
@RequestMapping("/image/")
public class ImageController {

    private Logger log = LoggerFactory.getLogger(ImageController.class);

    @Autowired
    private GroupImageBroker groupImageBroker;

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



}