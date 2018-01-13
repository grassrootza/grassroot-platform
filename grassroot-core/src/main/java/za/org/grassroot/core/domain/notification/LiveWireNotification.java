package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.MappedSuperclass;

/**
 * Created by luke on 2017/05/16.
 */
@MappedSuperclass
public abstract class LiveWireNotification extends Notification {

    protected LiveWireNotification() {
        // for JPA
    }

    protected LiveWireNotification(User destination, String message, LiveWireLog log) {
        super(destination, message, log);
        deliveryChannel = DeliveryRoute.SHORT_MESSAGE;
    }

    @Override
    public NotificationType getNotificationType() {
        return NotificationType.LIVEWIRE;
    }

    @Override
    public abstract NotificationDetailedType getNotificationDetailedType();

    @Override
    protected void appendToString(StringBuilder sb) {
        // needed for debugging etc
    }
}
