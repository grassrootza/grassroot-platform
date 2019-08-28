package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("SYSTEM_INFO")
public class SystemInfoNotification extends UserNotification {

    private SystemInfoNotification() {
        // for JPA
    }

    public SystemInfoNotification(User target, String message, UserLog userLog) {
        super(target, message, userLog);
        this.deliveryChannel = DeliveryRoute.LONG_SMS;
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.LANGUAGES;
    }
}
