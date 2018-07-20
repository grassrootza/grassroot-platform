package za.org.grassroot.core.domain.notification;

import lombok.Getter;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

// NB: don't ever use this directly (only use subclasses), hence no public constructor
@Entity
@DiscriminatorValue("BROADCAST")
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

    @Override
    public User getSender() {
        return broadcast != null ? broadcast.getCreatedByUser() : null;
    }

    // as above, don't use this outside queries
    protected BroadcastNotification() {
        // for JPA
    }

    @Override
    protected void appendToString(StringBuilder sb) {
        sb.append(", broadcast=").append(broadcast);
    }

    protected BroadcastNotification(User destination, String message, DeliveryRoute deliveryChannel,
                                    ActionLog actionLog, Broadcast broadcast) {
        super(destination, message, actionLog);
        this.deliveryChannel = deliveryChannel;
        this.broadcast = broadcast;
    }

}
