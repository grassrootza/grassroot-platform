package za.org.grassroot.core.domain.notification;

import lombok.Getter;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.DeliveryRoute;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class BroadcastNotification extends Notification {

    @ManyToOne
    @JoinColumn(name = "broadcast_id")
    @Getter private Broadcast broadcast;

    @Override
    public NotificationType getNotificationType() {
        return NotificationType.BROADCAST;
    }

    @Override
    public abstract NotificationDetailedType getNotificationDetailedType();

    protected BroadcastNotification() {
        // for JPA
    }

    @Override
    protected void appendToString(StringBuilder sb) {
        sb.append(", broadcast=").append(broadcast);
    }

    protected BroadcastNotification(User destination, String message, DeliveryRoute deliveryChannel,
                                    ActionLog actionLog) {
        super(destination, message, actionLog);
        this.deliveryChannel = deliveryChannel;
    }

}
