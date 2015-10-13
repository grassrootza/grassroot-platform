package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Created by aakilomar on 10/11/15.
 */
public class EventDTO  implements Serializable {
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

    /*
    used to calculate when a reminder must be sent, before the eventStartTime
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

    public EventDTO(String eventLocation, Long id, Timestamp createdDateTime, Timestamp eventStartDateTime, User createdByUser, Group appliesToGroup, boolean canceled, EventType eventType, String name, String dateTimeString, boolean includeSubGroups, int reminderMinutes, boolean rsvpRequired, boolean relayable, Integer version) {
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

    public EventDTO() {
    }

    public EventDTO(Event event) {
        this.eventLocation = event.getEventLocation();
        this.id = event.getId();
        this.createdDateTime = event.getCreatedDateTime();
        this.eventStartDateTime = event.getEventStartDateTime();
        this.createdByUser = event.getCreatedByUser();
        this.appliesToGroup = event.getAppliesToGroup();
        this.canceled = event.isCanceled();
        this.eventType = event.getEventType();
        this.name = event.getName();
        this.dateTimeString = event.getDateTimeString();
        this.includeSubGroups = event.isIncludeSubGroups();
        this.reminderMinutes = event.getReminderMinutes();
        this.rsvpRequired = event.isRsvpRequired();
        this.relayable = event.isRelayable();
        this.version = event.getVersion();

    }

    public Event getEventObject() {
        return new Event(eventLocation, id, createdDateTime, eventStartDateTime, createdByUser, appliesToGroup, canceled, eventType, name, dateTimeString, includeSubGroups, reminderMinutes, rsvpRequired, relayable, version);
    }

    public String getEventLocation() {
        return eventLocation;
    }

    public void setEventLocation(String eventLocation) {
        this.eventLocation = eventLocation;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Timestamp createdDateTime) {
        this.createdDateTime = createdDateTime;
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

    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDateTimeString() {
        return dateTimeString;
    }

    public void setDateTimeString(String dateTimeString) {
        this.dateTimeString = dateTimeString;
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

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    @Override
    public String toString() {
        return "EventDTO{" +
                "eventLocation='" + eventLocation + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", eventStartDateTime=" + eventStartDateTime +
                ", createdByUser=" + createdByUser +
                ", appliesToGroup.id=" + appliesToGroup +
                ", canceled=" + canceled +
                ", eventType=" + eventType +
                ", name='" + name + '\'' +
                ", dateTimeString='" + dateTimeString + '\'' +
                ", includeSubGroups=" + includeSubGroups +
                ", reminderMinutes=" + reminderMinutes +
                ", rsvpRequired=" + rsvpRequired +
                ", relayable=" + relayable +
                ", version=" + version +
                '}';
    }


}
