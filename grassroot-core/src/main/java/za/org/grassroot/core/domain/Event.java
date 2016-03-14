package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 * <p/>
 * Major todo: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * todo - aakil - add event duration
 */


import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Objects;


@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "type", discriminatorType = DiscriminatorType.STRING)
public abstract class Event implements Serializable {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "uid", length = 50)
	private String uid;

	@Column(name = "created_date_time", insertable = true, updatable = false)
	private Timestamp createdDateTime;

	/*
	For meetings this the meeting start time
	For voting this the vote expire time
	 */
	@Column(name = "start_date_time", nullable = false)
	private Timestamp eventStartDateTime;

	@ManyToOne
	@JoinColumn(name = "created_by_user", nullable = false)
	private User createdByUser;

	@ManyToOne
	@JoinColumn(name = "applies_to_group", nullable = false)
	private Group appliesToGroup;


	@Column(name = "canceled")
	private boolean canceled;

	/*
	could also have been called description but as group has a name, kept it the same
	 */
	@Column(name = "name", nullable = false)
	private String name;

	/*
	used to determine if notifications should be sent only to the group linked to the event, or any subgroups as well
	 */
	@Column(name = "includesubgroups", nullable = false)
	private boolean includeSubGroups;

	//todo aakil this feels a bit clunky, re-visit and see if there is not a cleaner way
	/*
	used to calculate when a reminder must be sent, before the eventStartTime
    if it is set to -1 it means there will be no reminders set for the event
    if it is set to 0, then we will take the reminderminutes from group if appliestogroup is not null
    if group = null or group.reminderminutes = 0 then set it to site.reminderminutes

     */
	@Column(name = "reminderminutes")
	private int reminderMinutes;

	/*
	Used primarily for meetings, to note if an RSVP is necessary
	Also used for voting, and will default to true for voting. Wont serve any purpose for voting at this stage.
	 */
	@Column(name = "rsvprequired", nullable = false)
	private boolean rsvpRequired;

	/*
	Used to determine if a recipient should have the option to forward an invite, vote, etc., when they receive it
	 */
	@Column(name = "can_relay", nullable = false)
	private boolean relayable;

	/*
	Used to prevent a formed entity from sending out when on the confirm screen of USSD
	 */
	@Column(name = "send_blocked", nullable = false)
	private boolean sendBlocked;

    /*
	Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */

	@Version
	private Integer version;

	/*
	Used to see if reminders have allready been sent for the event. It is not the number of messages
	sent but rather how many times we have sent reminders to participants.
	At the moment we only send once but thought in the future we might send more than once
	therefore the number rather than a boolean.

	N.B. will use this field for voting notification as well to determine if we already sent out the
	the vote results.
	 */
	@Column(name = "noreminderssent")
	private Integer noRemindersSent;

	public abstract EventType getEventType();

	protected Event() {
		// for JPA
	}

/*
	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired, boolean relayable) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.Meeting;
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.relayable = relayable;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.includeSubGroups = includeSubGroups;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser, Group appliesToGroup) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.appliesToGroup = appliesToGroup;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.sendBlocked = false;
	}

	public Event(User createdByUser, EventType eventType, boolean rsvpRequired) {
		this.createdByUser = createdByUser;
		this.eventType = eventType;
		this.rsvpRequired = rsvpRequired;
		this.sendBlocked = false;
	}

	public Event(String name, User createdByUser) {
		this.name = name;
		this.createdByUser = createdByUser;
		this.eventLocation = ""; // otherwise we get null violations
		this.eventType = EventType.MEETING;
		this.rsvpRequired = true; // this is our default
		this.sendBlocked = false;
	}

	public Event(User createdByUser, EventType eventType) {
		this.createdByUser = createdByUser;
		this.eventType = eventType;
		this.sendBlocked = false;
	}
*/

	protected Event(Timestamp createdDateTime, Timestamp eventStartDateTime, User user, Group group,
					boolean canceled, String name, boolean includeSubGroups, int reminderMinutes,
					boolean rsvpRequired, boolean relayable, boolean sendBlocked) {

		this.uid = UIDGenerator.generateId();
		this.eventStartDateTime = Objects.requireNonNull(eventStartDateTime);
		this.name = Objects.requireNonNull(name);
		this.createdByUser = Objects.requireNonNull(user);
		this.appliesToGroup = Objects.requireNonNull(group);
		this.createdDateTime = Objects.requireNonNull(createdDateTime);

		this.includeSubGroups = includeSubGroups;
		this.rsvpRequired = rsvpRequired;
		this.canceled = canceled;
		this.reminderMinutes = reminderMinutes;
		this.relayable = relayable;
		this.sendBlocked = sendBlocked;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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

	public Timestamp getCreatedDateTime() {
		return createdDateTime;
	}

	public void setCreatedDateTime(Timestamp createdDateTime) {
		this.createdDateTime = createdDateTime;
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

	public boolean isCanceled() {
		return canceled;
	}

	public void setCanceled(boolean canceled) {
		this.canceled = canceled;
	}

	public boolean isIncludeSubGroups() {
		return includeSubGroups;
	}

	public void setIncludeSubGroups(boolean includeSubGroups) {
		this.includeSubGroups = includeSubGroups;
	}

	public int getReminderMinutes() {
		return reminderMinutes;
	}

	public void setReminderMinutes(int reminderMinutes) {
		this.reminderMinutes = reminderMinutes;
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

	public boolean isSendBlocked() {
		return sendBlocked;
	}

	public void setSendBlocked(boolean sendBlocked) {
		this.sendBlocked = sendBlocked;
	}

	public Integer getVersion() {
		return version;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	public Integer getNoRemindersSent() {
		return noRemindersSent;
	}

	public void setNoRemindersSent(Integer noRemindersSent) {
		this.noRemindersSent = noRemindersSent;
	}

	@PreUpdate
	@PrePersist
	public void updateTimeStamps() {
		if (createdDateTime == null) {
			createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
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

	@Override
	public String toString() {
		return getClass().getSimpleName() + "{" +
				"eventName='" + name + '\'' +
				", uid='" + uid + '\'' +
				", id=" + id +
				", createdDateTime=" + createdDateTime +
				", eventStartDateTime=" + eventStartDateTime +
				", createdByUser=" + createdByUser +
				", appliesToGroup=" + appliesToGroup +
				", name='" + name + '\'' +
				", rsvpRequired=\'" + rsvpRequired + '\'' +
				", includeSubGroups=" + includeSubGroups + '\'' +
				", reminderMinutes=" + reminderMinutes + '\'' +
				", sendBlocked=" + sendBlocked + '\'' +
				", canceled=" + canceled + '\'' +
				", version=" + version + '\'' +

				'}';
	}

}
