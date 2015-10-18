package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 *
 * Major todo: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * todo - aakil - add event duration
 */


import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Calendar;


@Entity
@Table(name="event")
public class Event implements Serializable {
    private String eventLocation;
    private Long id;
    private Timestamp createdDateTime;
    private Timestamp eventStartDateTime;

    private User createdByUser;
    private Group appliesToGroup;
    private boolean canceled;

    private EventType eventType;

    /*
    could also have been called description but as group has a name, kept it the same
     */
    private String name;
    /*
    for various reasons at present we want to be able to store date and time as strings without being forced to
    parse and convert into a timestamp -- might move these into a Meeting sub-class, or handle in controller, but
    doing it this way for now. to clean up.
     */
    private String dateTimeString;
    /*
    used to determine if notifications should be sent only to the group linked to the event, or any subgroups as well
     */
    private boolean includeSubGroups;

    //todo aakil this feels a bit clunky, re-visit and see if there is not a cleaner way
    /*
    used to calculate when a reminder must be sent, before the eventStartTime
    if it is set to -1 it means there will be no reminders set for the event
    if it is set to 0, then we will take the reminderminutes from group if appliestogroup is not null
    if group = null or group.reminderminutes = 0 then set it to site.reminderminutes

     */
    private int reminderMinutes;

    /*
    Used for meetings, to note if an RSVP is necessary
     */
    private boolean rsvpRequired;

    /*
    Used to determine if a recipient should have the option to forward an invite, vote, etc., when they receive it
     */
    private boolean relayable;

    /*
    Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */

    private Integer version;

    /*
    Used to see if reminders have allready been sent for the event. It is not the number of messages
    sent but rather how many times we have sent reminders to participants.
    At the moment we only send once but thought in the future we might send more than once
    therefore the number rather than a boolean.
     */
    private Integer noRemindersSent;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    public Long getId() { return id; }

    public void setId(Long id) { this.id = id; }

    @Basic
    @Column(name="location", length=50)
    public String getEventLocation() { return eventLocation; }

    public void setEventLocation(String eventLocation) { this.eventLocation = eventLocation; }

    @Basic
    @Column(name="start_date_time")
    public Timestamp getEventStartDateTime() { return eventStartDateTime; }

    public void setEventStartDateTime(Timestamp eventStartDateTime) { this.eventStartDateTime = eventStartDateTime; }

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    public Timestamp getCreatedDateTime() { return createdDateTime; }

    public void setCreatedDateTime(Timestamp createdDateTime) { this.createdDateTime = createdDateTime; }

    @ManyToOne
    @JoinColumn(name="created_by_user")
    public User getCreatedByUser() { return createdByUser; }

    public void setCreatedByUser(User createdByUser) { this.createdByUser = createdByUser; }

    @ManyToOne
    @JoinColumn(name="applies_to_group")
    public Group getAppliesToGroup() { return appliesToGroup; }

    public void setAppliesToGroup(Group appliesToGroup) { this.appliesToGroup = appliesToGroup; }

    @Column
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Column
    public String getDateTimeString() { return dateTimeString; }

    public void setDateTimeString(String dateTimeString) { this.dateTimeString = dateTimeString; }

    @Column
    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    @Enumerated
    public EventType getEventType() { return eventType; }

    public void setEventType(EventType eventType) { this.eventType = eventType; }

    @Column(name = "includesubgroups")
    public boolean isIncludeSubGroups() {
        return includeSubGroups;
    }

    public void setIncludeSubGroups(boolean includeSubGroups) {
        this.includeSubGroups = includeSubGroups;
    }

    @Column(name = "reminderminutes")
    public int getReminderMinutes() {
        return reminderMinutes;
    }

    public void setReminderMinutes(int reminderMinutes) {
        this.reminderMinutes = reminderMinutes;
    }

    @Column(name = "rsvprequired")
    public boolean isRsvpRequired() {
        return rsvpRequired;
    }

    public void setRsvpRequired(boolean rsvpRequired) {
        this.rsvpRequired = rsvpRequired;
    }

    @Column(name = "can_relay")
    public boolean isRelayable() { return relayable; }

    public void setRelayable(boolean relayable) { this.relayable = relayable; }

    @Version
    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Column(name = "noreminderssent")
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


    /*
    Constructors
    Note: for the moment, until we build the use cases for other event types, defaulting all to meeting
     */


    public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups, boolean rsvpRequired, boolean relayable){
        this.name = name;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.eventLocation=""; // otherwise we get null violations
        this.eventType = EventType.Meeting;
        this.includeSubGroups = includeSubGroups;
        this.rsvpRequired = rsvpRequired;
        this.relayable = relayable;
    }

    public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups,boolean rsvpRequired) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.eventLocation=""; // otherwise we get null violations
        this.eventType = EventType.Meeting;
        this.includeSubGroups = includeSubGroups;
        this.rsvpRequired = rsvpRequired;
    }

    public Event(String name, User createdByUser, Group appliesToGroup, boolean includeSubGroups) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.eventLocation=""; // otherwise we get null violations
        this.eventType = EventType.Meeting;
        this.includeSubGroups = includeSubGroups;
    }

    public Event(String name, User createdByUser, Group appliesToGroup) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.eventLocation=""; // otherwise we get null violations
        this.eventType = EventType.Meeting;
    }

    public Event(User createdByUser, EventType eventType, boolean rsvpRequired) {
        this.createdByUser = createdByUser;
        this.eventType = eventType;
        this.rsvpRequired = rsvpRequired;
    }

    public Event(String name, User createdByUser) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.eventLocation=""; // otherwise we get null violations
        this.eventType = EventType.Meeting;
        this.rsvpRequired = true; // this is our default
    }

    public Event(User createdByUser, EventType eventType) {
        this.createdByUser = createdByUser;
        this.eventType = eventType;
    }

    public Event(String eventLocation, Long id, Timestamp createdDateTime, Timestamp eventStartDateTime, User createdByUser, Group appliesToGroup, boolean canceled, EventType eventType, String name, String dateTimeString, boolean includeSubGroups, int reminderMinutes, boolean rsvpRequired, boolean relayable, Integer version) {
        this.eventLocation = eventLocation;
        this.id = id;
        this.createdDateTime = createdDateTime;
        this.eventStartDateTime = eventStartDateTime;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.canceled = canceled;
        this.eventType = eventType;
        this.name = name;
        this.dateTimeString = dateTimeString;
        this.includeSubGroups = includeSubGroups;
        this.reminderMinutes = reminderMinutes;
        this.rsvpRequired = rsvpRequired;
        this.relayable = relayable;
        this.version = version;
    }

    public Event() {
    }

    public boolean minimumEquals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Event event = (Event) o;

        /* if (dateTimeString != null ? !dateTimeString.equals(event.dateTimeString) : event.dateTimeString != null)
            return false;*/
        if (eventLocation != null ? !eventLocation.equals(event.eventLocation) : event.eventLocation != null)
            return false;
        if (eventStartDateTime != null ? !eventStartDateTime.equals(event.eventStartDateTime) : event.eventStartDateTime != null)
            return false;
        if (name != null ? !name.equals(event.name) : event.name != null) return false;

        return true;

    }

    @Override
    public String toString() {
        return "Event{" +
                "eventName='" + name + '\'' +
                ", eventLocation='" + eventLocation + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", eventStartDateTime=" + eventStartDateTime +
                ", createdByUser=" + createdByUser +
                ", appliesToGroup=" + appliesToGroup +
                ", name='" + name + '\'' +
                ", dateTimeString=\'" + dateTimeString +'\'' +
                ", rsvpRequired=\'" + rsvpRequired + '\'' +
                ", includeSubGroups=" + includeSubGroups +'\'' +
                ", reminderMinutes=" + reminderMinutes +'\'' +
                ", version=" + version +'\'' +

                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Event event = (Event) o;

        if (canceled != event.canceled) return false;
        if (includeSubGroups != event.includeSubGroups) return false;
        if (reminderMinutes != event.reminderMinutes) return false;
        if (appliesToGroup != null ? !appliesToGroup.equals(event.appliesToGroup) : event.appliesToGroup != null)
            return false;
        if (createdByUser != null ? !createdByUser.equals(event.createdByUser) : event.createdByUser != null)
            return false;
        if (createdDateTime != null ? !createdDateTime.equals(event.createdDateTime) : event.createdDateTime != null)
            return false;
        if (dateTimeString != null ? !dateTimeString.equals(event.dateTimeString) : event.dateTimeString != null)
            return false;
        if (eventLocation != null ? !eventLocation.equals(event.eventLocation) : event.eventLocation != null)
            return false;
        if (eventStartDateTime != null ? !eventStartDateTime.equals(event.eventStartDateTime) : event.eventStartDateTime != null)
            return false;
        if (eventType != event.eventType) return false;
        if (id != null ? !id.equals(event.id) : event.id != null) return false;
        if (name != null ? !name.equals(event.name) : event.name != null) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = eventLocation != null ? eventLocation.hashCode() : 0;
        result = 31 * result + (id != null ? id.hashCode() : 0);
        result = 31 * result + (createdDateTime != null ? createdDateTime.hashCode() : 0);
        result = 31 * result + (eventStartDateTime != null ? eventStartDateTime.hashCode() : 0);
        result = 31 * result + (createdByUser != null ? createdByUser.hashCode() : 0);
        result = 31 * result + (appliesToGroup != null ? appliesToGroup.hashCode() : 0);
        result = 31 * result + (canceled ? 1 : 0);
        result = 31 * result + (eventType != null ? eventType.hashCode() : 0);
        result = 31 * result + (name != null ? name.hashCode() : 0);
        result = 31 * result + (dateTimeString != null ? dateTimeString.hashCode() : 0);
        result = 31 * result + (includeSubGroups ? 1 : 0);
        result = 31 * result + reminderMinutes;
        return result;
    }
}
