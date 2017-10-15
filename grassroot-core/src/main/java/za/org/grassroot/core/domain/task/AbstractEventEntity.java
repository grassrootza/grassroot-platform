package za.org.grassroot.core.domain.task;

import org.hibernate.annotations.Type;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * This class should contain all fields common to both Event and EventRequest entity
 */
@MappedSuperclass
public abstract class AbstractEventEntity {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	protected Long id;

	@Column(name = "uid", length = 50, unique = true)
	protected String uid;

	@Column(name = "created_date_time", insertable = true, updatable = false)
	protected Instant createdDateTime;

	@Column(name = "name", length = 40)
	protected String name;

	@Column(name = "description", length = 512)
	protected String description;

	/*
	For meetings, this is just a tag, maybe used later -- for votes, it is the vote options
	(and hence also needed in vote request for assembling)
	 */
	@Column(name = "tags")
	@Type(type = "za.org.grassroot.core.util.StringArrayUserType")
	protected String[] tags;

	/*
	For meetings this the meeting start time
	For voting this the vote expire time
	 */
	@Column(name = "start_date_time", nullable = false)
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
	@JoinColumn(name = "parent_action_todo_id")
	protected Todo parentTodo;

	protected AbstractEventEntity() {
		// for JPA
	}

	protected AbstractEventEntity(String name, Instant eventStartDateTime, User createdByUser,
								  boolean includeSubGroups, boolean rsvpRequired, boolean relayable,
								  EventReminderType reminderType, int customReminderMinutes, String description) {
		this.uid = UIDGenerator.generateId();
		this.createdDateTime = Instant.now();

		this.name = Objects.requireNonNull(name);
		this.eventStartDateTime = Objects.requireNonNull(eventStartDateTime);
		this.createdByUser = Objects.requireNonNull(createdByUser);
		this.includeSubGroups = includeSubGroups;
		this.reminderType = Objects.requireNonNull(reminderType);
		this.customReminderMinutes = customReminderMinutes;
		this.description = (description == null) ? "" : description;
		// this.tags = new String[0];

		this.rsvpRequired = rsvpRequired;
		this.relayable = relayable;
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

	public String getName() {
		return name;
	}

	public boolean hasName() {
		return !StringUtils.isEmpty(name);
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

	public String[] getTags() { return tags; }

	public List<String> getVoteOptions() {
		return StringArrayUtil.arrayToList(getTags());
	}

	public void addVoteOption(String option) {
		ArrayList<String> currentOptions = new ArrayList<>(Arrays.asList(getTags()));
		currentOptions.add(option);
		this.tags = StringArrayUtil.listToArrayRemoveDuplicates(currentOptions);
	}

	public void setVoteOptions(List<String> options) {
		Objects.requireNonNull(options);
		this.tags = StringArrayUtil.listToArray(options);
	}

	public void setTags(String[] tags) {
		this.tags = tags;
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

		return uid != null ? uid.equals(event.uid) : event.uid == null;

	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

}
