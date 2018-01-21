package za.org.grassroot.webapp.controller.rest.user;

import io.swagger.annotations.Api;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.user.UserManagementService;
import za.org.grassroot.webapp.controller.rest.BaseRestController;
import za.org.grassroot.webapp.controller.rest.Grassroot2RestController;
import za.org.grassroot.webapp.model.rest.NotificationDTO;

import javax.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@RestController
@Grassroot2RestController
@Api("/api/user/notifications")
@RequestMapping(value = "/api/user/notifications")
public class UserNotificationsController extends BaseRestController {


    private final NotificationService notificationService;

    public UserNotificationsController(JwtService jwtService, UserManagementService userManagementService, NotificationService notificationService) {
        super(jwtService, userManagementService);
        this.notificationService = notificationService;
    }

    @RequestMapping("/list")
    public ResponseEntity<List<NotificationDTO>> listUnreadNotifications(HttpServletRequest request) {
        User loggedInUser = getUserFromRequest(request);

        if (loggedInUser == null)
            return new ResponseEntity<>((List<NotificationDTO>) null, HttpStatus.UNAUTHORIZED);

        List<Notification> notifications = notificationService.fetchUnreadUserNotifications(loggedInUser, new Sort(Sort.Direction.DESC, "createdDateTime"));
        List<NotificationDTO> dtos = notifications.stream()
                .filter(NotificationDTO::isNotificationOfTypeForDTO) // may want to loosen this in future
                .map(NotificationDTO::convertToDto)
                .collect(Collectors.toList());
        //have to trim dates in order to be recognized by javascript
        dtos.forEach(dto -> {
            dto.setCreatedDatetime(dto.getCreatedDatetime().substring(0, 19));
            dto.setDeadlineDateTime(dto.getDeadlineDateTime().substring(0, 19));
        });

        return ResponseEntity.ok(dtos);
    }

    @RequestMapping("/mark-read")
    public ResponseEntity<Map<String, Object>> markRead(HttpServletRequest request, @RequestParam String notificationUid) {

        User loggedInUser = getUserFromRequest(request);
        Map<String, Object> result = new HashMap<>();

        if (loggedInUser == null)
            return new ResponseEntity<>(new HashMap<>(), HttpStatus.UNAUTHORIZED);

        Notification notification = notificationService.loadNotification(notificationUid);
        if (notification == null)
            return new ResponseEntity<>(new HashMap<>(), HttpStatus.BAD_REQUEST);

        if (!notification.getTarget().equals(loggedInUser))
            return new ResponseEntity<>(new HashMap<>(), HttpStatus.FORBIDDEN);

        notificationService.updateNotificationsViewedAndRead(Collections.singleton(notificationUid));
        result.put("success", Boolean.TRUE);
        return ResponseEntity.ok(result);
    }
}
