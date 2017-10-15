package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.services.group.GroupImageBroker;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapperImpl;
import za.org.grassroot.webapp.util.ImageUtil;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Collections;

/**
 * Created by luke on 2016/09/28.
 */
@RestController
@RequestMapping(value = "/api/group/", produces = MediaType.APPLICATION_JSON_VALUE)
public class GroupImageRestController extends GroupAbstractRestController {

    private static final Logger log = LoggerFactory.getLogger(GroupImageRestController.class);

    @Autowired
    private GroupImageBroker groupImageBroker;

    @RequestMapping(value = "/image/default/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> changeGroupDefault(@PathVariable String phoneNumber, @PathVariable String code,
                                                              @RequestParam String groupUid, @RequestParam GroupDefaultImage defaultImage) {

        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            log.info("removing any custom image, and updating default image to : {}", defaultImage);
            groupImageBroker.setGroupImageToDefault(user.getUid(), groupUid, defaultImage, true);
            Group updatedGroup = groupBroker.load(groupUid);
            return RestUtil.okayResponseWithData(RestMessage.UPLOADED, Collections.singletonList(createGroupWrapper(updatedGroup, user)));
        } catch (AccessDeniedException e) {
            return RestUtil.errorResponse(HttpStatus.FORBIDDEN, RestMessage.PERMISSION_DENIED);
        }
    }

    @RequestMapping(value = "/image/upload/{phoneNumber}/{code}/{groupUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadImage(@PathVariable String phoneNumber, @PathVariable String code, @PathVariable String groupUid,
                                                       @RequestParam("image") MultipartFile file, HttpServletRequest request) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        Group group = groupBroker.load(groupUid);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        ResponseEntity<ResponseWrapper> responseEntity;
        if (file != null) {
            try {
                byte[] image = file.getBytes();
                String fileName = ImageUtil.generateFileName(file,request);
                groupImageBroker.saveGroupImage(user.getUid(), groupUid, fileName, image);
                Group updatedGroup = groupBroker.load(groupUid);
                responseEntity = RestUtil.okayResponseWithData(RestMessage.UPLOADED, Collections.singletonList(createGroupWrapper(updatedGroup, user)));
            } catch (IOException | IllegalArgumentException e) {
                log.info("error "+e.getLocalizedMessage());
                responseEntity = new ResponseEntity<>(new ResponseWrapperImpl(HttpStatus.NOT_ACCEPTABLE, RestMessage.BAD_PICTURE_FORMAT,
                        RestStatus.FAILURE), HttpStatus.NOT_ACCEPTABLE);
            }
        } else {
            responseEntity = RestUtil.errorResponse(HttpStatus.NOT_ACCEPTABLE, RestMessage.PICTURE_NOT_RECEIVED);
        }

        return responseEntity;

    }

}
