package za.org.grassroot.core.domain.task;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * This class should contain all fields common to both to-do and TodoRequest entity
 */
@MappedSuperclass @Getter
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

	@Enumerated(EnumType.STRING)
	@Column(name = "todo_type", nullable = false)
	protected TodoType type;

	@Basic
	@Column(name = "response_tag")
	@Setter protected String responseTag;

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
	@Setter protected Instant actionByDate;

	@Column(name = "message")
	@Setter protected String message;

	@Column(name = "description", length = 512)
	@Setter protected String description;

	/*
	Minus value will send a reminder before actionByDate, Plus value will send a reminder x minutes before
	actionByDate. Note ... now that it's fixed, this shares a lot with event, so consider abstracting to task
	*/
	@Column(name = "reminder_minutes")
	@Setter protected int reminderMinutes;

	@Column(name = "reminder_active")
	@Setter protected boolean reminderActive;

	protected AbstractTodoEntity() {
		// for JPA
	}

	protected AbstractTodoEntity(User createdByUser, TodoContainer parent, TodoType type,
                                 String explanation, Instant actionByDate, int reminderMinutes, boolean reminderActive) {

		this.uid = UIDGenerator.generateId();
		this.createdByUser = Objects.requireNonNull(createdByUser);
		this.type = type;
		setParent(parent);

		this.message = Objects.requireNonNull(explanation);
		this.actionByDate = Objects.requireNonNull(actionByDate);
		this.createdDateTime = Instant.now();

		this.reminderMinutes = reminderMinutes;
		this.reminderActive = reminderActive;
	}

	public LocalDateTime getActionByDateAtSAST() { return actionByDate.atZone(DateTimeUtil.getSAST()).toLocalDateTime(); }

	public LocalDateTime getCreatedDateTimeAtSAST() {
		return createdDateTime.atZone(DateTimeUtil.getSAST()).toLocalDateTime();
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
