package za.org.grassroot.webapp.controller.rest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.NotificationDTO;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.services.exception.NotificationAlreadyUpdatedException;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.GenericResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.NotificationWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapper;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.security.AccessControlException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/04/13.
 */
@RestController
@RequestMapping("/api/notification")
public class NotificationRestController {

    private static final Logger log = LoggerFactory.getLogger(NotificationRestController.class);

    private static final int pageLength = 20;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private UserManagementService userManagementService;


    @RequestMapping(value = "/list/{phoneNumber}/{code}", method = RequestMethod.GET)
    public ResponseEntity<ResponseWrapper> getNotifications(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                            @RequestParam(value = "page", required = false) Integer page,
                                                            @RequestParam(value = "size", required = false) Integer size) {

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        page = (page == null) ? 0 : page;
        size = (size == null) ? pageLength : size;

        log.info("getNotifications ... trying to retrieve pageable");
        Page<Notification> pageable = notificationService.getNotificationsByTarget(user, page, size);
        log.info("getNotifications ... user has {} notifications in total", pageable.getTotalElements());

        ResponseWrapper responseWrapper;
        if (page > pageable.getTotalPages()) {
            responseWrapper = new ResponseWrapperImpl(HttpStatus.BAD_REQUEST, RestMessage.NOTIFICATIONS, RestStatus.FAILURE);
        } else {
            List<String> notificationUid = pageable.getContent().stream().map(n -> n.getUid()).collect(Collectors.toList());
            log.info("notificaton_uid size={}",notificationUid.size());
            List<NotificationDTO> notificationDTOList = notificationService.fetchNotificationDTOs(notificationUid);
            log.info("notificationDTOList sizr ={}", notificationDTOList.size());
            NotificationWrapper notificationWrapper = new NotificationWrapper(pageable, notificationDTOList);
            responseWrapper = new GenericResponseWrapper(HttpStatus.OK, RestMessage.NOTIFICATIONS, RestStatus.SUCCESS, notificationWrapper);
        }
        return new ResponseEntity<>(responseWrapper,HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @RequestMapping(value = "/update/read/{phoneNumber}/{code}", method = RequestMethod.POST)
    public ResponseEntity<ResponseWrapper> updateReadStatus(@PathVariable("phoneNumber") String phoneNumber, @PathVariable("code") String code,
                                                            @RequestParam("uid") String uid) throws Exception{

        User user = userManagementService.loadOrSaveUser(phoneNumber);
        Notification notification = notificationService.loadNotification(uid);

        log.info("updating notification read status for user uid : {}, notification uid : {}", user.getUid(), uid);
        if (!notification.getTarget().equals(user)) {
            throw new AccessControlException("Error! Trying to set another user's notification status");
        }

        ResponseWrapper responseWrapper;
        if(notification.isRead()) {
            log.info("Trying to update notification when already read");
            responseWrapper = new ResponseWrapperImpl(HttpStatus.ALREADY_REPORTED, RestMessage.ALREADY_UPDATED, RestStatus.FAILURE);
        } else {
            notificationService.updateNotificationReadStatus(uid,true);
            responseWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.NOTIFICATION_UPDATED, RestStatus.SUCCESS);
        }
        return new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }

    @ExceptionHandler(NotificationAlreadyUpdatedException.class)
    public ResponseEntity<ResponseWrapper> handleException(){

        ResponseWrapper responseWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.ALREADY_UPDATED, RestStatus.FAILURE);
        return  new ResponseEntity<>(responseWrapper, HttpStatus.valueOf(responseWrapper.getCode()));

    }
}
