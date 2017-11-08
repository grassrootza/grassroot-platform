package za.org.grassroot.webapp.controller.android1;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.integration.exception.NoMicroVersionException;
import za.org.grassroot.integration.exception.StoredMediaRetrievalFailure;
import za.org.grassroot.services.task.TaskImageBroker;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.model.rest.ImageRecordDTO;
import za.org.grassroot.webapp.model.rest.wrappers.ResponseWrapper;
import za.org.grassroot.webapp.util.RestUtil;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.stream.Collectors;

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
    public ResponseEntity<ResponseWrapper> uploadTaskImage(@PathVariable String phoneNumber, @PathVariable String taskUid, @PathVariable TaskType taskType,
                                                           @RequestParam(required = false) Double longitude, @RequestParam(required = false) Double latitude,
                                                           @RequestParam("image") MultipartFile file, HttpServletRequest request) {

        User user = userManagementService.findByInputNumber(phoneNumber);

        logger.info("uploading an image with long={}, lat={}", longitude, latitude);

        try {
            String actionLogUid = taskImageBroker.storeImageForTask(user.getUid(), taskUid, taskType, file, null, longitude, latitude);
            return !StringUtils.isEmpty(actionLogUid) ?
                    RestUtil.okayResponseWithData(RestMessage.MEETING_IMAGE_ADDED, actionLogUid) :
                    RestUtil.errorResponse(RestMessage.MEETING_IMAGE_ERROR);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/list/{phoneNumber}/{code}/{taskType}/{taskUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> fetchImageRecords(@PathVariable String phoneNumber, @PathVariable TaskType taskType,
                                                             @PathVariable String taskUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        logger.info("finding images for task of type {} and UID {}", taskType, taskUid);
        List<ImageRecordDTO> records = taskImageBroker.fetchImagesForTask(user.getUid(), taskUid, taskType)
                .stream()
                .map(i -> new ImageRecordDTO(taskImageBroker.fetchLogForImage(i.getActionLogUid(), taskType), i))
                .collect(Collectors.toList());
        return RestUtil.okayResponseWithData(records.isEmpty() ? RestMessage.TASK_NO_IMAGES : RestMessage.TASK_IMAGES_FOUND,
                records);
    }

    @RequestMapping(value = "/count/{phoneNumber}/{code}/{taskType}/{taskUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> countImageRecords(@PathVariable String phoneNumber, @PathVariable TaskType taskType,
                                                             @PathVariable String taskUid) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        return RestUtil.okayResponseWithData(RestMessage.TASK_IMAGE_COUNT,
                taskImageBroker.countImagesForTask(user.getUid(), taskUid, taskType));
    }

    @RequestMapping(value = "/record/{phoneNumber}/{code}/{taskType}/{logUid}", method = RequestMethod.GET)
    public ResponseEntity<?> fetchTaskImageRecord(@PathVariable String phoneNumber, @PathVariable TaskType taskType,
                                                  @PathVariable String logUid) {
        ImageRecordDTO record = new ImageRecordDTO(
                taskImageBroker.fetchLogForImage(logUid, taskType),
                taskImageBroker.fetchImageRecord(logUid, taskType)
        );
        return RestUtil.okayResponseWithData(RestMessage.TASK_IMAGE_RECORD, record);
    }

    @RequestMapping(value = "/fetch/{phoneNumber}/{code}/{taskType}/{logUid}", method = RequestMethod.GET)
    public ResponseEntity<?> fetchTaskImage(@PathVariable TaskType taskType, @PathVariable String logUid) {
        // will add a check for user/event membership in future, maybe, hence parameter, but for now minimizing calls
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            byte[] image = taskImageBroker.fetchImageForTask(null, taskType, logUid, true);
            return ResponseEntity.ok()
                    .lastModified(24000)
                    .headers(headers)
                    .body(image);
        } catch (StoredMediaRetrievalFailure e) {
            return RestUtil.errorResponse(HttpStatus.BAD_REQUEST, RestMessage.TASK_IMAGE_ERROR);
        }
    }

    @RequestMapping(value = "/fetch/micro/{phoneNumber}/{code}/{taskType}/{logUid}", method = RequestMethod.GET)
    public ResponseEntity<?> fetchTaskThumbnail(@PathVariable TaskType taskType, @PathVariable String logUid) {
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_JPEG);
            byte[] image = taskImageBroker.fetchMicroThumbnailForTask(null, taskType, logUid);
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(image);
        } catch (NoMicroVersionException e) {
            logger.info("failed, going for the full image ...");
            return fetchTaskImage(taskType, logUid);
        }
    }

    @RequestMapping(value = "/update/faces/{phoneNumber}/{code}/{taskType}/{logUid}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> updateFaceCount(@PathVariable String phoneNumber, @PathVariable TaskType taskType,
                                                           @PathVariable String logUid, @RequestParam int numberFaces) {
        User user = userManagementService.findByInputNumber(phoneNumber);
        try {
            taskImageBroker.updateImageFaceCount(user.getUid(), logUid, taskType, numberFaces);
            ImageRecordDTO updatedImage = new ImageRecordDTO(taskImageBroker.fetchLogForImage(logUid, taskType),
                    taskImageBroker.fetchImageRecord(logUid, taskType));
            return RestUtil.okayResponseWithData(RestMessage.IMAGE_FACES_UPDATED, updatedImage);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

    @RequestMapping(value = "/delete/{phoneNumber}/{code}")
    public ResponseEntity<ResponseWrapper> removeTaskImage(@PathVariable String phoneNumber, @RequestParam TaskType taskType,
                                                           @RequestParam String logUid,
                                                           @RequestParam(required = false) Boolean leaveStored) {
        try {
            User user = userManagementService.findByInputNumber(phoneNumber);
            String removalLogUid =  taskImageBroker.removeTaskImageRecord(user.getUid(), taskType, logUid, leaveStored == null || !leaveStored);
            return RestUtil.okayResponseWithData(RestMessage.IMAGE_RECORD_REMOVED, removalLogUid);
        } catch (AccessDeniedException e) {
            return RestUtil.accessDeniedResponse();
        }
    }

}
