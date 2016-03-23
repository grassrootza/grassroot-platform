package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
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
	protected Timestamp createdDateTime;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	@JoinColumn(name = "created_by_user_id", nullable = false)
	protected User createdByUser;

	@ManyToOne(cascade = CascadeType.ALL, optional = false)
	@JoinColumn(name = "group_id", nullable = false)
	protected Group group;

	@Column(name = "action_by_date")
	protected Timestamp actionByDate;

	@Column(name = "message")
	protected String message;

	/*
	Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes after
	actionByDate
	*/
	@Column(name = "reminder_minutes")
	protected int reminderMinutes;

	@ManyToOne(cascade = CascadeType.ALL)
	@JoinColumn(name = "assigned_to_user_id")
	protected User assignedToUser;

	private static final int DEFAULT_REMINDER_MINUTES = -1440;

	protected AbstractLogBookEntity() {
		// for JPA
	}

	protected AbstractLogBookEntity(User createdByUser, Group group, Group replicatedGroup, String message, Timestamp actionByDate, User assignedToUser, int reminderMinutes) {
		this.createdByUser = Objects.requireNonNull(createdByUser);
		this.group = Objects.requireNonNull(group);
		this.message = Objects.requireNonNull(message);
		this.actionByDate = Objects.requireNonNull(actionByDate);
		this.reminderMinutes = reminderMinutes == 0? DEFAULT_REMINDER_MINUTES : reminderMinutes;

		this.uid = UIDGenerator.generateId();
		this.createdDateTime = Timestamp.from(Instant.now());
		this.assignedToUser = assignedToUser;
	}

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Timestamp getCreatedDateTime() {
		return createdDateTime;
	}

	public User getCreatedByUser() {
		return createdByUser;
	}

	public Group getGroup() {
		return group;
	}

	public void setGroup(Group group) {
		this.group = group;
	}

	public Timestamp getActionByDate() {
		return actionByDate;
	}

	public void setActionByDate(Timestamp actionByDate) {
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

	public User getAssignedToUser() {
		return assignedToUser;
	}

	public void setAssignedToUser(User assignedToUser) {
		this.assignedToUser = assignedToUser;
	}
}
