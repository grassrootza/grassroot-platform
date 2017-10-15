package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity
@DiscriminatorValue("GROUP_WELCOME")
public class GroupWelcomeNotification extends AccountNotification {

    protected GroupWelcomeNotification() {
        // for JPA
    }

    // note: this extends account notification because it is only possible on a paid account
    public GroupWelcomeNotification(User destination, String message, AccountLog accountLog) {
        super(destination, message, accountLog);
        if (accountLog.getGroup() == null) {
            throw new IllegalArgumentException("Account log must have group attached for this notification type");
        }
        this.priority = AlertPreference.NOTIFY_ONLY_NEW.getPriority(); // since account product so should make sure to send (but may revise)
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.GROUP_WELCOME;
    }
}
