package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * This class should contain all fields common to both to-do and TodoRequest entity
 */
@MappedSuperclass
public abstract class AbstractTodoEntity {

	public static final int DEFAULT_REMINDER_MINUTES = -1440;

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

	/*
	Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */

	@Version
	private Integer version;

	// -------------------------------------------------------------
	// THESE SHOULD BE OF PRIVATE VISIBILITY; BECAUSE EVERYONE ELSE
	// SHOULD READ/WRITE THEM VIA getParent()/setParent() !!!
	// ONLY DIRECT JPQL/SQL QUERIES SHOULD USE THIS IF NECESSARY!
	// -------------------------------------------------------------

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "parent_group_id")
	private Group parentGroup;

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "parent_event_id")
	private Event parentEvent;

	// --------------------------------------------------------------

	@Column(name = "action_by_date")
	protected Instant actionByDate;

	@Column(name = "message")
	protected String message;

	@Column(name = "description", length = 512)
	protected String description;

	/*
	Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes before
	actionByDate. Note ... now that it's fixed, this shares a lot with event, so consider abstracting to task
	*/
	@Column(name = "reminder_minutes")
	protected int reminderMinutes;

	@Column(name = "scheduled_reminder_time")
	private Instant scheduledReminderTime;

	@Column(name = "reminder_active")
	private boolean reminderActive;

	protected AbstractTodoEntity() {
		// for JPA
	}

	protected AbstractTodoEntity(User createdByUser, TodoContainer parent, String message, Instant actionByDate,
	                             int reminderMinutes, boolean reminderActive) {

		this.uid = UIDGenerator.generateId();
		this.createdByUser = Objects.requireNonNull(createdByUser);
		setParent(parent);

		this.message = Objects.requireNonNull(message);
		this.actionByDate = Objects.requireNonNull(actionByDate);
		this.createdDateTime = Instant.now();

		this.reminderMinutes = reminderMinutes;
		this.reminderActive = reminderActive;
		calculateScheduledReminderTime();

	}

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
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

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public int getReminderMinutes() {
		return reminderMinutes;
	}

	public void setReminderMinutes(int reminderMinutes) {
		this.reminderMinutes = reminderMinutes;
	}

	public Instant getScheduledReminderTime() { return scheduledReminderTime; }

	public LocalDateTime getReminderTimeAtSAST() { return scheduledReminderTime.atZone(DateTimeUtil.getSAST()).toLocalDateTime(); }

	public void setScheduledReminderTime(Instant scheduledReminderTime) { this.scheduledReminderTime = scheduledReminderTime; }

	public boolean isReminderActive() { return reminderActive; }

	public void setReminderActive(boolean reminderActive) { this.reminderActive = reminderActive; }

	public void calculateScheduledReminderTime() {
		this.scheduledReminderTime = reminderActive
				? DateTimeUtil.restrictToDaytime(actionByDate.minus(reminderMinutes, ChronoUnit.MINUTES), actionByDate,
				DateTimeUtil.getSAST()) : null;

		// if reminder time is already in the past (e.g., set to 1 week but deadline in 5 days), try set it to tomorrow, else set it to deadline
		if (reminderActive && this.scheduledReminderTime.isBefore(Instant.now())) {
			if (Instant.now().plus(1, ChronoUnit.DAYS).isBefore(actionByDate)) {
				this.scheduledReminderTime = DateTimeUtil.restrictToDaytime(Instant.now().plus(1, ChronoUnit.DAYS),
						actionByDate, DateTimeUtil.getSAST());
			} else {
				this.scheduledReminderTime = actionByDate;
			}
		}
	}

	public TodoContainer getParent() {
		if (parentGroup != null) {
			return parentGroup;
		} else if (parentEvent != null) {
			return parentEvent;
		} else {
			throw new IllegalStateException("There is no " + TodoContainer.class.getSimpleName() + " parent defined for " + this);
		}
	}

	public void setParent(TodoContainer parent) {
		Objects.requireNonNull(parent);
		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
		} else if (parent instanceof Event) {
			this.parentEvent = (Event) parent;
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

		Todo todo = (Todo) o;

		return uid != null ? uid.equals(todo.uid) : todo.uid == null;
	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

}
