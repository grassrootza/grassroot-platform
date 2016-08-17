package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by paballo on 2016/06/30.
 */

@Entity
@DiscriminatorValue("JOINREQUEST")
public class JoinRequestNotification extends UserNotification {

    private JoinRequestNotification() {
        // for JPA
    }

    public JoinRequestNotification(User target, String message, UserLog userLog) {
        super(target, message, userLog);
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.JOINREQUEST;
    }
}
