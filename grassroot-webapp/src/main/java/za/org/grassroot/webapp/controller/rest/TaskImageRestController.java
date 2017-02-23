package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

/**
 * Created by luke on 2017/02/21.
 */
@RestController
@RequestMapping(value = "/api/task/image/", produces = MediaType.APPLICATION_JSON_VALUE)
public class TaskImageRestController {

    public static final Logger logger = LoggerFactory.getLogger(TaskImageRestController.class);

    private final UserManagementService userManagementService;
    private final TaskImageBroker taskImageBroker;

    @Autowired
    public TaskImageRestController(UserManagementService userManagementService, TaskImageBroker taskImageBroker) {
        this.userManagementService = userManagementService;
        this.taskImageBroker = taskImageBroker;
    }

    @RequestMapping(value = "/upload/{phoneNumber}/{code}/{taskType}/{taskUid}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> uploadTaskImage(@PathVariable String phoneNumber, @PathVariable String taskUid,
                                                           @PathVariable TaskType taskType, @RequestParam("image") MultipartFile file) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        try {
            String actionLogUid = taskImageBroker.storeImageForTask(user.getUid(), taskUid, taskType, file);
            return !StringUtils.isEmpty(actionLogUid) ?
                    RestUtil.okayResponseWithData(RestMessage.MEETING_IMAGE_ADDED, actionLogUid) :
                    RestUtil.errorResponse(RestMessage.MEETING_IMAGE_ERROR);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

}
