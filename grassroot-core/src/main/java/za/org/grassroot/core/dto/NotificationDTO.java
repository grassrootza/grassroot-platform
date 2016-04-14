package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.Notification;

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
    private boolean delivered;
    private boolean read;

    public NotificationDTO(Notification notification){
        this.uid = notification.getUid();
        this.entityUid = notification.getEventLog().getEvent().getUid();
        this.title = notification.getEventLog().getEvent().resolveGroup().getGroupName();
        this.message = notification.getMessage();
        this.delivered=notification.isDelivered();
        this.read =notification.isRead();
        this.notificationType = notification.getNotificationType().toString();
        this.createdDatetime=notification.getCreatedDateTime().toString();
    }

    public NotificationDTO(Notification notification, LogBook logBook){
        this.uid = notification.getUid();
        this.entityUid = logBook.getUid();
        this.title = logBook.resolveGroup().getGroupName();
        this.message = notification.getMessage();
        this.delivered=notification.isDelivered();
        this.read =notification.isRead();
        this.notificationType = notification.getNotificationType().toString();

        this.createdDatetime=notification.getCreatedDateTime().toString();

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


}
