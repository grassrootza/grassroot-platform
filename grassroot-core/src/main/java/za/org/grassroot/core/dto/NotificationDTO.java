package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.enums.TaskType;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.format.DateTimeFormatter;

/**
 * Created by paballo on 2016/04/13.
 */
public class NotificationDTO {

    private String uid;
    private String entityUid;
    private String title;
    private String message;
    private String createdDatetime;
    private String notificationType;
    private String entityType;
    private boolean delivered;
    private boolean read;

    public NotificationDTO(Notification notification, Event event) {
        this.uid = notification.getUid();
        this.entityUid = event.getUid();
        this.title = event.resolveGroup().getGroupName();
        this.message = (notification.getMessage() != null) ? notification.getMessage() : notification.getEventLog().getMessage();
        this.delivered = notification.isDelivered();
        this.read = notification.isRead();
        this.notificationType = notification.getNotificationType().toString();
        this.createdDatetime= getLocalDateTime(notification.getCreatedDateTime());
        this.entityType = event.getEventType().toString();
    }

    public NotificationDTO(Notification notification, LogBook logBook){
        this.uid = notification.getUid();
        this.entityUid = logBook.getUid();
        this.title = logBook.resolveGroup().getGroupName();
        this.message = (notification.getMessage() != null) ? notification.getMessage() : notification.getLogBookLog().getMessage();
        this.delivered=notification.isDelivered();
        this.read =notification.isRead();
        this.notificationType = TaskType.TODO.toString();
        this.createdDatetime=getLocalDateTime(notification.getCreatedDateTime());
        this.entityType = TaskType.TODO.toString();

    }

    public String getEntityType() {
        return entityType;
    }

    public String getUid() {
        return uid;
    }

    public String getEntityUid() {
        return entityUid;
    }

    public String getTitle() {
        return title;
    }

    public String getMessage() {
        return message;
    }

    public String getCreatedDatetime() {
        return createdDatetime;
    }

    public String getNotificationType() {
        return notificationType;
    }




    public boolean isDelivered() {
        return delivered;
    }

    public boolean isRead() {
        return read;
    }

    private String getLocalDateTime(Instant instant ) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy HH:mm");
        return DateTimeUtil.convertToUserTimeZone(instant, DateTimeUtil.getSAST()).format(formatter);
    }


}
