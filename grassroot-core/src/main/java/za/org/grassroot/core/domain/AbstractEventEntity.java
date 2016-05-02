package za.org.grassroot.core.domain;

import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * This class should contain all fields common to both Event and EventRequest entity
 */
@MappedSuperclass
public abstract class AbstractEventEntity<P extends UidIdentifiable> {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	protected Long id;

	@Column(name = "uid", length = 50)
	protected String uid;

	@Column(name = "created_date_time", insertable = true, updatable = false)
	protected Instant createdDateTime;

	@Column(name = "name", length = 40)
	protected String name;

	@Column(name = "description", length = 512)
	protected String description;

	/*
	For meetings this the meeting start time
	For voting this the vote expire time
	 */
	@Column(name = "start_date_time")
	protected Instant eventStartDateTime;

	@ManyToOne
	@JoinColumn(name = "created_by_user")
	protected User createdByUser;

	/*
	used to determine if notifications should be sent only to the group linked to the event, or any subgroups as well
	 */
	@Column(name = "includesubgroups")
	protected boolean includeSubGroups;

	/*
	Used primarily for meetings, to note if an RSVP is necessary
	Also used for voting, and will default to true for voting. Wont serve any purpose for voting at this stage.
	 */
	@Column(name = "rsvprequired")
	protected boolean rsvpRequired;

	/*
	Used to determine if a recipient should have the option to forward an invite, vote, etc., when they receive it
	 */
	@Column(name = "can_relay")
	protected boolean relayable;

	@Enumerated(EnumType.STRING)
	@Column(name = "reminder_type", length = 50)
	protected EventReminderType reminderType;

	@Column(name = "custom_reminder_minutes")
	protected int customReminderMinutes;

	// -------------------------------------------------------------
	// THESE SHOULD BE OF PRIVATE VISIBILITY; BECAUSE EVERYONE ELSE
	// SHOULD READ/WRITE THEM VIA getParent()/setParent() !!!
	// ONLY DIECT JPQL/SQL QUERIES SHOULD USE THIS IF NECESSARY!
	// -------------------------------------------------------------
	@ManyToOne
	@JoinColumn(name = "parent_group_id")
	protected Group parentGroup;

	@ManyToOne
	@JoinColumn(name = "parent_log_book_id")
	protected LogBook parentLogBook;

	public abstract void setParent(P parent);

	public abstract P getParent();

	protected AbstractEventEntity() {
		// for JPA
	}

	protected AbstractEventEntity(String name, Instant eventStartDateTime, User createdByUser, UidIdentifiable parent,
								  boolean includeSubGroups, boolean rsvpRequired, boolean relayable,
								  EventReminderType reminderType, int customReminderMinutes, String description) {
		this.uid = UIDGenerator.generateId();
		this.createdDateTime = Instant.now();

		this.name = Objects.requireNonNull(name);
		this.eventStartDateTime = Objects.requireNonNull(eventStartDateTime);
		this.createdByUser = Objects.requireNonNull(createdByUser);
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.relayable = relayable;
		this.reminderType = Objects.requireNonNull(reminderType);
		this.customReminderMinutes = customReminderMinutes;
		this.description = (description == null) ? "" : description;


		if (parent instanceof Group) {
			this.parentGroup = (Group) parent;
		} else if (parent instanceof LogBook) {
			this.parentLogBook = (LogBook) parent;
		} else {
			throw new UnsupportedOperationException("Unsupported parent: " + parent);
		}
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUid() {
		return uid;
	}

	public Instant getCreatedDateTime() {
		return createdDateTime;
	}

	public void setCreatedDateTime(Instant createdDateTime) {
		this.createdDateTime = createdDateTime;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Instant getEventStartDateTime() {
		return eventStartDateTime;
	}

	public LocalDateTime getEventDateTimeAtSAST() {
		return eventStartDateTime.atZone(DateTimeUtil.getSAST()).toLocalDateTime();
	}

	public void setEventStartDateTime(Instant eventStartDateTime) {
		this.eventStartDateTime = eventStartDateTime;
	}

	public User getCreatedByUser() {
		return createdByUser;
	}

	public void setCreatedByUser(User createdByUser) {
		this.createdByUser = createdByUser;
	}

	public boolean isIncludeSubGroups() {
		return includeSubGroups;
	}

	public void setIncludeSubGroups(boolean includeSubGroups) {
		this.includeSubGroups = includeSubGroups;
	}

	public boolean isRsvpRequired() {
		return rsvpRequired;
	}

	public void setRsvpRequired(boolean rsvpRequired) {
		this.rsvpRequired = rsvpRequired;
	}

	public boolean isRelayable() {
		return relayable;
	}

	public void setRelayable(boolean relayable) {
		this.relayable = relayable;
	}

	public EventReminderType getReminderType() {
		return reminderType;
	}

	public void setReminderType(EventReminderType reminderType) {
		this.reminderType = reminderType;
	}

	public int getCustomReminderMinutes() {
		return customReminderMinutes;
	}

	public void setCustomReminderMinutes(int customReminderMinutes) {
		this.customReminderMinutes = customReminderMinutes;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		Event event = (Event) o;

		if (uid != null ? !uid.equals(event.uid) : event.uid != null) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

}
