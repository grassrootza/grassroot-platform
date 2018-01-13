package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class AccountNotification extends Notification {
	@ManyToOne
	@JoinColumn(name = "account_id")
	private Account account;

	protected AccountNotification() {
		// for JPA
	}

	public AccountNotification(User destination, String message, AccountLog accountLog) {
        super(destination, message, accountLog);
//        this.deliveryChannel = DeliveryRoute.SMS;
        this.account = accountLog.getAccount();
		this.setUseOnlyFreeChannels(true);
	}

	@Override
	public NotificationType getNotificationType() {
		return NotificationType.ACCOUNT;
	}

	@Override
	public abstract NotificationDetailedType getNotificationDetailedType();

	public Account getAccount() {
		return account;
	}

	@Override
	protected void appendToString(StringBuilder sb) {
		sb.append(", account=").append(account);
	}
}
