package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("JOIN_CODE")
public class JoinCodeNotification extends UserNotification {

    private JoinCodeNotification() {
        // for JPA
    }

    public JoinCodeNotification(User destination, String message, UserLog userLog){
        super(destination, message, userLog);
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.SEND_JOIN_CODE;
    }
}
