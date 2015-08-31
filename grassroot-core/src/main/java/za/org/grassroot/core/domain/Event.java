package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 *
 * Major todo: Add relationship to user who created event
 * Major todo: Add relationship to group that is participating in event
 * Major todo: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * Other: All todo's as for User class
 * todo - aakil - add event duration
 * todo - aakil - add logic to trigger sending of notification when minimum data saved and user(s) added - @observer??
 */


import org.apache.commons.lang3.SerializationUtils;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.event.EventChangeEvent;
import za.org.grassroot.core.util.ContextHelper;

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

    /*
    could also have been called description but as group has a name, kept it the same
     */
    private String name;
    /*
    for various reasons at present we want to be able to store date and time as strings without being forced to
    parse and convert into a timestamp -- might move these into a Meeting sub-class, or handle in controller, but
    doing it this way for now. to clean up.
     */
    private String dayOfEvent;
    private String timeOfEvent;

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
    public String getDayOfEvent() { return dayOfEvent; }

    public void setDayOfEvent(String dayOfEvent) { this.dayOfEvent = dayOfEvent; }

    @Column
    public String getTimeOfEvent() { return timeOfEvent; }

    public void setTimeOfEvent(String timeOfEvent) { this.timeOfEvent = timeOfEvent; }

    @Column
    public boolean isCanceled() {
        return canceled;
    }

    public void setCanceled(boolean canceled) {
        this.canceled = canceled;
    }

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }

    @PostPersist
    @PostUpdate
    public void notifyChange() {
        System.out.println("notifyChange...previous..." + previousEvent);
        if (previousEvent != null) {
            if (!previousEvent.minimumDataAvailable() && this.minimumDataAvailable() && !canceled) {
                // let's start sending out the notifications
                EventChangeEvent ce = new EventChangeEvent(this, EventChangeType.EVENT_ADDED.toString());
                ContextHelper.getPublisher().publishEvent(ce);
                System.out.println("notifyChange...raised...event..." + EventChangeType.EVENT_ADDED.toString());



            }
            if (previousEvent.minimumDataAvailable() && this.minimumDataAvailable() && !canceled) {
                // let's send out a change notification
                //todo but first see if something actually changed
                EventChangeEvent ce = new EventChangeEvent(this,EventChangeType.EVENT_CHANGED.toString());
                ContextHelper.getPublisher().publishEvent(ce);
                System.out.println("notifyChange...raised...event..." + EventChangeType.EVENT_CHANGED.toString());

            }
            if (!previousEvent.isCanceled() && canceled) {
                // ok send out cancelation notifications
                EventChangeEvent ce = new EventChangeEvent(this,EventChangeType.EVENT_CANCELLED.toString());
                ContextHelper.getPublisher().publishEvent(ce);
                System.out.println("notifyChange...raised...event..." + EventChangeType.EVENT_CANCELLED.toString());

            }

        } else {
            System.out.println("notifyChange...check for minimumdata no previous..." );

            if (minimumDataAvailable() && !canceled) {
                // let's start sending out the notifications
                System.out.println("notifyChange...send-new-notifications...sent");

            }

        }
    }

    @Transient
    public boolean minimumDataAvailable() {
        boolean minimum = true;
        System.out.println("minimumDataAvailable..." + this.toString());
        if (name == null || name.trim().equals("")) minimum = false;
        if (eventLocation == null || eventLocation.trim().equals("")) minimum = false;
        if (appliesToGroup == null ) minimum = false;
        if (createdByUser == null) minimum = false;
        if (dayOfEvent == null || dayOfEvent.trim().equals("")) minimum = false;
        if (timeOfEvent == null || timeOfEvent.trim().equals("")) minimum = false;
        System.out.println("minimumDataAvailable...returning..." + minimum);

        return minimum;
    }

    @Transient
    private Event previousEvent;

    @PostLoad
    public void saveState() {
        this.previousEvent = SerializationUtils.clone(this);
    }



    /*
    Constructors
     */

    public Event(String name, User createdByUser, Group appliesToGroup) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.appliesToGroup = appliesToGroup;
        this.eventLocation=""; // otherwise we get null violations
    }

    public Event(String name, User createdByUser) {
        this.name = name;
        this.createdByUser = createdByUser;
        this.eventLocation=""; // otherwise we get null violations
    }

    public Event() {
    }

    // todo: stop this causing a stack overflow whenever it is called
    @Override
    public String toString() {
        return "Event{" +
                "eventLocation='" + eventLocation + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", eventStartDateTime=" + eventStartDateTime +
                ", createdByUser=" + createdByUser +
                ", appliesToGroup=" + appliesToGroup +
                ", name='" + name + '\'' +
                ", dayOfEvent=" + dayOfEvent + '\'' +
                ", timeOfEvent=" + timeOfEvent +'\'' +
                '}';
    }
}
