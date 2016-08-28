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
@DiscriminatorValue("JOINREQUEST_RESULT")
public class JoinRequestResultNotification extends UserNotification {

    private JoinRequestResultNotification() {
        // for JPA
    }

    public JoinRequestResultNotification(User target, String message, UserLog userLog) {
        super(target, message, userLog);
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.JOINREQUEST;
    }
}
