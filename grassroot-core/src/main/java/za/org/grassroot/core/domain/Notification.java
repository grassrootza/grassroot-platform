package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/**
 * Next attempt time signifies when this notification has to be sent. When it gets delivered,
 * this timestamp is set as null.
 */
@Entity
@Table(name = "notification")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
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

	@Column(name = "next_attempt_time")
	private Instant nextAttemptTime;

	@Column(name = "last_attempt_time")
	private Instant lastAttemptTime;

	@Column(name = "attempt_count", nullable = false)
	private int attemptCount = 0;

	@ManyToOne
	@JoinColumn(name = "target_id")
	private User target;

	@ManyToOne
	@JoinColumn(name = "event_log_id")
	private EventLog eventLog;

	@ManyToOne
	@JoinColumn(name = "log_book_log_id")
	private LogBookLog logBookLog;

	@ManyToOne
	@JoinColumn(name = "group_log_id", foreignKey = @ForeignKey(name = "fk_notification_group_log"))
	private GroupLog groupLog;

	@ManyToOne
	@JoinColumn(name = "account_log_id", foreignKey = @ForeignKey(name = "fk_notification_account_log"))
	private AccountLog accountLog;

	@ManyToOne
	@JoinColumn(name = "user_log_id", foreignKey = @ForeignKey(name = "fk_notification_user_log"))
	private UserLog userLog;

	@Column(name = "read")
	private boolean read = false;

	@Column(name = "delivered")
	private boolean delivered = false;

	@Column(name = "message")
	protected String message;

	@Transient
	public int priority;

	public abstract NotificationType getNotificationType();

	protected Notification() {
		// for JPA
	}

	protected Notification(User target, String message, ActionLog actionLog) {
		this.uid = UIDGenerator.generateId();
		this.read = false;

		this.target = Objects.requireNonNull(target); // at least for now, Notifications are always targeted to a user
		this.createdDateTime = Instant.now();
		this.nextAttemptTime = createdDateTime; // default is to be sent immediately
		this.message = Objects.requireNonNull(message);
		this.priority = DEFAULT_PRIORITY;

		if (actionLog instanceof EventLog) {
			eventLog = (EventLog) actionLog;
		} else if (actionLog instanceof GroupLog) {
			groupLog = (GroupLog) actionLog;
		} else if (actionLog instanceof LogBookLog) {
			logBookLog = (LogBookLog) actionLog;
		} else if (actionLog instanceof AccountLog) {
			accountLog = (AccountLog) actionLog;
		} else if (actionLog instanceof UserLog) {
			userLog = (UserLog) actionLog;
		} else {
			throw new IllegalArgumentException("Unsupported action log: " + actionLog);
		}
	}

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Instant getCreatedDateTime() {
		return createdDateTime;
	}

	public User getTarget() {
		return target;
	}

	public boolean isRead() {
		return read;
	}

	public void setRead(boolean read) {
		this.read = read;
	}

	public boolean isDelivered() {
		return nextAttemptTime == null;
	}

	public EventLog getEventLog() {
		return eventLog;
	}

	public LogBookLog getLogBookLog() {
		return logBookLog;
	}

	public GroupLog getGroupLog() {
		return groupLog;
	}

	public AccountLog getAccountLog() {
		return accountLog;
	}

	public UserLog getUserLog() {
		return userLog;
	}

	public String getMessage() {
		return message;
	}

	public Instant getNextAttemptTime() {
		return nextAttemptTime;
	}

	public void setNextAttemptTime(Instant nextAttemptTime) {
		this.nextAttemptTime = nextAttemptTime;
	}

	public void markAsDelivered() {
		this.delivered = true;
		this.nextAttemptTime = null;
	}

	public int getAttemptCount() {
		return attemptCount;
	}

	public void incrementAttemptCount() {
		this.attemptCount++;
	}

	public Instant getLastAttemptTime() {
		return lastAttemptTime;
	}

	public void setLastAttemptTime(Instant lastAttemptTime) {
		this.lastAttemptTime = lastAttemptTime;
	}

	public int getPriority() {
		return priority;
	};

	public void setPriority(int priority){
		this.priority = priority;
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

		if (uid != null ? !uid.equals(that.uid) : that.uid != null) {
			return false;
		}

		return true;
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
		sb.append(", attemptCount=").append(attemptCount);
		sb.append(", delivered=").append(delivered);
		sb.append(", read=").append(read);
		sb.append(", createdDateTime=").append(createdDateTime);
		sb.append(", nextAttemptTime=").append(nextAttemptTime);
		sb.append(", lastAttemptTime=").append(lastAttemptTime);
		sb.append('}');
		return sb.toString();
	}

	/**
	 * This is a way for subclass to add its own specific toString information
	 * @param sb StringBuilder
	 */
	protected abstract void appendToString(StringBuilder sb);
}
