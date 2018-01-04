package za.org.grassroot.core.domain;


import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.campaign.CampaignLog;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.MessagingProvider;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Next attempt time signifies when this notification has to be sent. When it gets delivered,
 * this timestamp is set as null.
 */
@Entity
@Table(name = "notification")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
@Getter
public abstract class Notification implements Serializable {
	// default priority for notification is not overriden with getPriority();
	private static final int DEFAULT_PRIORITY = 1;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "uid", nullable = false, unique = true)
	private String uid;

	@Column(name = "creation_time", insertable = true, updatable = false)
	private Instant createdDateTime;

	@Column(name = "attempt_count", nullable = false)
	@Setter private int sendAttempts = 0;

	@ManyToOne
	@JoinColumn(name = "target_id")
	private User target;

	@Column(name = "sending_status")
    @Enumerated(EnumType.STRING)
    @Setter private NotificationStatus status = NotificationStatus.READY_FOR_SENDING;

	@Setter
	@Column(name = "send_only_after")
	private Instant sendOnlyAfter;

	@Column(name = "last_status_change")
	private Instant lastStatusChange;

    @Setter
    @Column(name = "sending_key")
    protected String sendingKey;

	@Setter
	@Column(name = "delivery_channel")
    @Enumerated(EnumType.STRING)
	protected DeliveryRoute deliveryChannel = DeliveryRoute.SMS; //defaults to SMS

	@ManyToOne
	@JoinColumn(name = "event_log_id")
	private EventLog eventLog;

	@ManyToOne
	@JoinColumn(name = "action_todo_log_id")
	@Setter private TodoLog todoLog;

	@ManyToOne
	@JoinColumn(name = "group_log_id", foreignKey = @ForeignKey(name = "fk_notification_group_log"))
	protected GroupLog groupLog;

	@ManyToOne
	@JoinColumn(name = "account_log_id", foreignKey = @ForeignKey(name = "fk_notification_account_log"))
	private AccountLog accountLog;

	@ManyToOne
	@JoinColumn(name = "user_log_id", foreignKey = @ForeignKey(name = "fk_notification_user_log"))
	private UserLog userLog;

	@ManyToOne
	@JoinColumn(name = "livewire_log_id", foreignKey = @ForeignKey(name = "fk_notification_livewire_log"))
	private LiveWireLog liveWireLog;

	@ManyToOne
	@JoinColumn(name = "campaign_log_id", foreignKey = @ForeignKey(name = "fk_notification_campaign_log"))
	private CampaignLog campaignLog;

	@Column(name = "message")
	protected String message;

	@Setter
	@Column(name = "sent_via_provider")
	@Enumerated(EnumType.STRING)
	private MessagingProvider sentViaProvider = null;


	@Setter
	@Column(name = "read_receipt_fetches")
	private int readReceiptFetchAttempts;

	@Setter
	@Column(name = "use_only_free_channels")
	private boolean useOnlyFreeChannels = false;


	@ElementCollection
	@CollectionTable(name = "notification_error", joinColumns = @JoinColumn(name = "notification_id"))
	private List<NotificationSendError> sendingErrors = new ArrayList<>();


	@Transient
	public int priority;

	public abstract NotificationType getNotificationType();

	public abstract NotificationDetailedType getNotificationDetailedType();

	protected Notification() {
		// for JPA
	}

	protected Notification(User target, String message, ActionLog actionLog) {
		this.uid = UIDGenerator.generateId();

		this.target = Objects.requireNonNull(target); // at least for now, Notifications are always targeted to a user
		this.createdDateTime = Instant.now();
		this.lastStatusChange = createdDateTime;
		this.message = Objects.requireNonNull(message);
		this.readReceiptFetchAttempts = 0;

		if (this.message.length() > 255)
			this.message = message.substring(0, 255);

		this.priority = DEFAULT_PRIORITY;
		this.deliveryChannel = target.getMessagingPreference();

		if (actionLog instanceof EventLog) {
			eventLog = (EventLog) actionLog;
		} else if (actionLog instanceof GroupLog) {
			groupLog = (GroupLog) actionLog;
		} else if (actionLog instanceof TodoLog) {
			todoLog = (TodoLog) actionLog;
		} else if (actionLog instanceof AccountLog) {
			accountLog = (AccountLog) actionLog;
		} else if (actionLog instanceof UserLog) {
			userLog = (UserLog) actionLog;
		} else if (actionLog instanceof LiveWireLog) {
			liveWireLog = (LiveWireLog) actionLog;
		} else if (actionLog instanceof CampaignLog) {
			campaignLog = (CampaignLog) actionLog;
		} else {
			throw new IllegalArgumentException("Unsupported action log: " + actionLog);
		}

	}


	/**
	 * @param status                 status to be set
	 * @param resultOfSendingAttempt if this status update is result of sending attempt should be true, otherwise false
	 * @param resultOfReceiptFetch if this status update is result of getting a read receipt
	 */
	public void updateStatus(NotificationStatus status, boolean resultOfSendingAttempt, boolean resultOfReceiptFetch, String error) {
		NotificationStatus oldStatus = this.status;
		this.status = status;
		this.lastStatusChange = Instant.now();
		if (resultOfSendingAttempt)
			this.sendAttempts++;
		if (resultOfReceiptFetch)
			this.readReceiptFetchAttempts++;
		if (error != null) {
			NotificationSendError sendError = new NotificationSendError(LocalDateTime.now(), error, oldStatus, status);
			this.sendingErrors.add(sendError);
		}
	}

	// used in messaging service
	public void incrementReceiptFetchCount() {
		this.readReceiptFetchAttempts++;
	}

	/**
	 * @return group relevant for action that triggered this notification
	 */
	public Group getRelevantGroup() {

		if (this.eventLog != null)
			return eventLog.getEvent().getAncestorGroup();

		else if (this.groupLog != null)
			return this.groupLog.getGroup();

		else if (this.todoLog != null)
			return this.todoLog.getTodo().getAncestorGroup();

		else if (this.accountLog != null)
			return this.accountLog.getGroup();

		else if (this.liveWireLog != null)
			return this.liveWireLog.getAlert().getGroup();

		else return null;
	}


	public boolean isRead() {
		return this.status == NotificationStatus.READ;
	}

	public boolean isDelivered() {
		return this.status == NotificationStatus.DELIVERED || this.status == NotificationStatus.READ;
	}

	public boolean isViewedOnAndroid() {
		return this.deliveryChannel == DeliveryRoute.ANDROID_APP && this.status == NotificationStatus.READ;
	}

	public boolean isPrioritySatisfiedByTarget() {
		return this.priority >= target.getNotificationPriority();
	}

	/**
	 * Locale utilities
	 */

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Notification that = (Notification) o;

		return uid != null ? uid.equals(that.uid) : that.uid == null;

	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder(getClass().getSimpleName());
		sb.append("{id=").append(id);
		sb.append(", uid='").append(uid).append('\'');
		sb.append(", target=").append(target);
		appendToString(sb);
		sb.append(", sendAttempts=").append(sendAttempts);
		sb.append(", status=").append(status);
		sb.append(", createdDateTime=").append(createdDateTime);
		sb.append(", lastStatusChange=").append(lastStatusChange);
		sb.append('}');
		return sb.toString();
	}

	/**
	 * This is a way for subclass to add its own specific toString information
	 * @param sb StringBuilder
	 */
	protected abstract void appendToString(StringBuilder sb);
}
