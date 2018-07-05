package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("LANGUAGES")
public class UserLanguageNotification extends UserNotification {

    private UserLanguageNotification() {
        // for JPA
    }

    public UserLanguageNotification(User target, String message, UserLog userLog) {
        super(target, message, userLog);
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.LANGUAGES;
    }
}
