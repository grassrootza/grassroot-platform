package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

/**
 * Created by luke on 2016/10/25.
 */
@Entity
@DiscriminatorValue("ACCOUNT_BILLING_NOTIFICATION")
public class AccountBillingNotification extends AccountNotification {

    protected AccountBillingNotification() {
        // for JPA
    }

    public AccountBillingNotification(User destination, String message, AccountLog accountLog) {
        super(destination, message, accountLog);
        this.priority = AlertPreference.NOTIFY_NEW_AND_REMINDERS.getPriority(); // since this is an account 'product'
    }

    @Override
    public NotificationDetailedType getNotificationDetailedType() {
        return NotificationDetailedType.ACCOUNT_BILLING_NOTIFICATION;
    }
}
