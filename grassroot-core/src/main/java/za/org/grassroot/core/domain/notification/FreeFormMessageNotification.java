package za.org.grassroot.core.domain.notification;

import lombok.Builder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.enums.AlertPreference;
import za.org.grassroot.core.enums.NotificationDetailedType;

import javax.persistence.DiscriminatorValue;
import javax.persistence.Entity;

@Entity @Builder
@DiscriminatorValue("FREE_FORM_MESSAGE")
public class FreeFormMessageNotification extends AccountNotification {

	protected FreeFormMessageNotification() {
	}

	public FreeFormMessageNotification(User destination, String message, AccountLog accountLog) {
		super(destination, message, accountLog);
		this.priority = AlertPreference.NOTIFY_NEW_AND_REMINDERS.getPriority(); // since this is an account 'product'
	}

	@Override
	public NotificationDetailedType getNotificationDetailedType() {
		return NotificationDetailedType.FREE_FORM_MESSAGE;
	}
}
