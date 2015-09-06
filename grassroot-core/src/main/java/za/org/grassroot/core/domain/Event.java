package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 *
 * Major todo: Add relationship to user who created event
 * Major todo: Add relationship to group that is participating in event
 * Major todo: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * Other: All todo's as for User class
 * todo - aakil - add event duration
 */


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
    private String dateTimeString;

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

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
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

    @Override
    public String toString() {
        return "Event{" +
                "eventLocation='" + eventLocation + '\'' +
                ", id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", eventStartDateTime=" + eventStartDateTime +
                ", createdByUser=" + createdByUser +
 //               ", appliesToGroup=" + appliesToGroup +
                ", name='" + name + '\'' +
                ", dateTimeString=" + dateTimeString +'\'' +
                '}';
    }
}
