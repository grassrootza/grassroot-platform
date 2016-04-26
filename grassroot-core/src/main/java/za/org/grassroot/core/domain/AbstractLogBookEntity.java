package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;

/**
 * This class should contain all fields common to both LogBook and LogBookRequest entity
 */
@MappedSuperclass
public abstract class AbstractLogBookEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	protected Long id;

	@Column(name = "uid", nullable = false, unique = true)
	protected String uid;

	@Column(name = "created_date_time", updatable = false)
	protected Instant createdDateTime;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	protected User createdByUser;

	// -------------------------------------------------------------
	// THESE SHOULD BE OF PRIVATE VISIBILITY; BECAUSE EVERYONE ELSE
	// SHOULD READ/WRITE THEM VIA getParent()/setParent() !!!
	// -------------------------------------------------------------

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "group_id")
	private Group group;

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "event_id")
	private Event event;

	// --------------------------------------------------------------

	@Column(name = "action_by_date")
	protected Instant actionByDate;

	@Column(name = "message")
	protected String message;

	/*
	Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes after
	actionByDate
	*/
	@Column(name = "reminder_minutes")
	protected int reminderMinutes;

	private static final int DEFAULT_REMINDER_MINUTES = -1440;

	protected AbstractLogBookEntity() {
		// for JPA
	}

	protected AbstractLogBookEntity(User createdByUser, LogBookContainer parent, String message, Instant actionByDate, int reminderMinutes) {
		this.createdByUser = Objects.requireNonNull(createdByUser);
		setParent(parent);
		this.message = Objects.requireNonNull(message);
		this.actionByDate = Objects.requireNonNull(actionByDate);
		this.reminderMinutes = reminderMinutes == 0 ? DEFAULT_REMINDER_MINUTES : reminderMinutes;

		this.uid = UIDGenerator.generateId();
		this.createdDateTime = Instant.now();
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

	public User getCreatedByUser() {
		return createdByUser;
	}

	public Instant getActionByDate() {
		return actionByDate;
	}

	public LocalDateTime getActionByDateAtSAST() { return actionByDate.atZone(DateTimeUtil.getSAST()).toLocalDateTime(); }

	public void setActionByDate(Instant actionByDate) {
		this.actionByDate = actionByDate;
	}

	public String getMessage() {
		return message;
	}

	public void setMessage(String message) {
		this.message = message;
	}

	public int getReminderMinutes() {
		return reminderMinutes;
	}

	public void setReminderMinutes(int reminderMinutes) {
		this.reminderMinutes = reminderMinutes;
	}

	public LogBookContainer getParent() {
		if (group != null) {
			return group;
		} else if (event != null) {
			return event;
		} else {
			throw new IllegalStateException("There is no " + LogBookContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

	public void setParent(LogBookContainer parent) {
		Objects.requireNonNull(parent);
		if (parent instanceof Group) {
			this.group = (Group) parent;
		} else if (parent instanceof Event) {
			this.event = (Event) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		LogBook logBook = (LogBook) o;

		if (uid != null ? !uid.equals(logBook.uid) : logBook.uid != null) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

}
