package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Objects;

@MappedSuperclass
public class AbstractEventContent {
	/*
	could also have been called description but as group has a name, kept it the same
	 */
	@Column(name = "name")
	private String name;

	/*
	For meetings this the meeting start time
	For voting this the vote expire time
	 */
	@Column(name = "start_date_time")
	private Timestamp eventStartDateTime;

	@ManyToOne
	@JoinColumn(name = "created_by_user")
	private User createdByUser;

	@ManyToOne
	@JoinColumn(name = "applies_to_group")
	private Group appliesToGroup;

	/*
	used to determine if notifications should be sent only to the group linked to the event, or any subgroups as well
	 */
	@Column(name = "includesubgroups")
	private boolean includeSubGroups;

	/*
	Used primarily for meetings, to note if an RSVP is necessary
	Also used for voting, and will default to true for voting. Wont serve any purpose for voting at this stage.
	 */
	@Column(name = "rsvprequired")
	private boolean rsvpRequired;

	/*
	Used to determine if a recipient should have the option to forward an invite, vote, etc., when they receive it
	 */
	@Column(name = "can_relay")
	private boolean relayable;

	@Enumerated(EnumType.STRING)
	@Column(name = "reminder_type")
	private EventReminderType reminderType;

	@Column(name = "custom_reminder_minutes")
	private int customReminderMinutes;

	protected AbstractEventContent() {
		// for JPA
	}

	protected AbstractEventContent(String name, Timestamp eventStartDateTime, User createdByUser, Group appliesToGroup,
								boolean includeSubGroups, boolean rsvpRequired, boolean relayable,
								EventReminderType reminderType, int customReminderMinutes) {
		this.name = Objects.requireNonNull(name);
		this.eventStartDateTime = Objects.requireNonNull(eventStartDateTime);
		this.createdByUser = Objects.requireNonNull(createdByUser);
		this.appliesToGroup = Objects.requireNonNull(appliesToGroup);
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.relayable = relayable;
		this.reminderType = Objects.requireNonNull(reminderType);
		this.customReminderMinutes = customReminderMinutes;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public Timestamp getEventStartDateTime() {
		return eventStartDateTime;
	}

	public void setEventStartDateTime(Timestamp eventStartDateTime) {
		this.eventStartDateTime = eventStartDateTime;
	}

	public User getCreatedByUser() {
		return createdByUser;
	}

	public void setCreatedByUser(User createdByUser) {
		this.createdByUser = createdByUser;
	}

	public Group getAppliesToGroup() {
		return appliesToGroup;
	}

	public void setAppliesToGroup(Group appliesToGroup) {
		this.appliesToGroup = appliesToGroup;
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

	public void checkRequiredFields() {
		Objects.requireNonNull(name);
		Objects.requireNonNull(eventStartDateTime);
		Objects.requireNonNull(appliesToGroup);
		Objects.requireNonNull(createdByUser);
		Objects.requireNonNull(reminderType);
	}
}
