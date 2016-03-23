package za.org.grassroot.core.dto;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;

import java.io.Serializable;
import java.sql.Timestamp;

/**
 * Created by aakilomar on 10/11/15.
 */
public class EventDTO  implements Serializable {

    private static final Logger log = LoggerFactory.getLogger(EventDTO.class);

    private String eventLocation;
    private Long id;
    private Timestamp createdDateTime;
    private Timestamp eventStartDateTime;

    private User createdByUser;
    private Group appliesToGroup;
    private boolean canceled;
    private EventType eventType;

    /*
    calling it this instead of just Uid because the new objects created in getEventObject will have own Uid
     */
    private String eventUid;

    /*
    could also have been called description but as group has a name, kept it the same
     */
    private String name;
    private String description;
    /*
    used to determine if notifications should be sent only to the group linked to the event, or any subgroups as well
     */
    private boolean includeSubGroups;

    /*
    used to calculate when a reminder must be sent, before the eventStartTime
     */
    private int customReminderMinutes;

    /*
    Used for meetings, to note if an RSVP is necessary
     */
    private boolean rsvpRequired;

    /*
    Used to determine if a recipient should have the option to forward an invite, vote, etc., when they receive it
     */
    private boolean relayable;

    /*
    Used to block messages from sending when still at confirmation screen in USSD
     */
    private boolean sendBlocked;

    /*
    Version used by hibernate to resolve conflicting updates. Do not update set it, it is for Hibernate only
     */

    private Integer version;
    /*
    Message field is used to override the reminder message created from template
     */
    private String message;

    private EventReminderType reminderType;

    public EventDTO() {
    }

    public EventDTO(Event event) {
        this.id = event.getId();
        this.eventUid = event.getUid();
        this.createdDateTime = event.getCreatedDateTime();
        this.eventStartDateTime = event.getEventStartDateTime();
        this.createdByUser = event.getCreatedByUser();
        this.appliesToGroup = event.getAppliesToGroup();
        this.canceled = event.isCanceled();
        this.eventType = event.getEventType();
        this.name = event.getName();
        this.description = event.getDescription();
        this.includeSubGroups = event.isIncludeSubGroups();
        this.reminderType = event.getReminderType();
        this.customReminderMinutes = event.getCustomReminderMinutes();
        this.rsvpRequired = event.isRsvpRequired();
        this.relayable = event.isRelayable();
        this.version = event.getVersion();
        this.message = "";
    }

    public EventDTO(Meeting meeting) {
        this ((Event) meeting);
        log.info("Inside the meeting specific constructor ... ");
        this.eventLocation = meeting.getEventLocation();
    }

    public Event getEventObject() {
        if (eventType.equals(EventType.MEETING)) {
            return new Meeting(name, eventStartDateTime, createdByUser, appliesToGroup, eventLocation, includeSubGroups, rsvpRequired, relayable, reminderType, customReminderMinutes, description);
        } else {
            return new Vote(name, eventStartDateTime, createdByUser, appliesToGroup, includeSubGroups, relayable, description);
        }
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

    public String getEventUid() { return this.eventUid; }

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

    public boolean isIncludeSubGroups() {
        return includeSubGroups;
    }

    public void setIncludeSubGroups(boolean includeSubGroups) {
        this.includeSubGroups = includeSubGroups;
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

    public EventReminderType getReminderType() {
        return reminderType;
    }

    public void setReminderType(EventReminderType reminderType) {
        this.reminderType = reminderType;
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

    public boolean isSendBlocked() { return sendBlocked; }

    public void setSendBlocked(boolean sendBlocked) { this.sendBlocked = sendBlocked; }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "EventDTO{" +
                "eventLocation='" + eventLocation + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", eventStartDateTime=" + eventStartDateTime +
                ", createdByUser=" + createdByUser +
                ", appliesToGroup=" + appliesToGroup +
                ", canceled=" + canceled +
                ", eventType=" + eventType +
                ", name='" + name + '\'' +
                ", includeSubGroups=" + includeSubGroups +
                ", reminderType=" + reminderType +
                ", customReminderMinutes=" + customReminderMinutes +
                ", rsvpRequired=" + rsvpRequired +
                ", relayable=" + relayable +
                ", sendBlocked=" + sendBlocked +
                ", version=" + version +
                ", message='" + message + '\'' +
                '}';
    }
}
