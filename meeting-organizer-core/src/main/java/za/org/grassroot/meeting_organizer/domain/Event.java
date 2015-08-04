package za.org.grassroot.meeting_organizer.domain;

/**
 * Created by luke on 2015/07/16.
 *
 * Major to-do: Add relationship to user who created event
 * Major to-do: Add relationship to group that is participating in event
 * Major to-do: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * Other: All to-dos as for User class
 */

import javax.persistence.*;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.Calendar;

@Entity
@Table(name="event")
public class Event {
    private String eventLocation;
    private Integer id;
    private Timestamp createdDateTime;
    private Timestamp eventStartDateTime;

    private User createdByUser;
    private Group appliesToGroup;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    public Integer getId() { return id; }

    public void setId(Integer id) { this.id = id; }

    @Basic
    @Column(name="location", length=50, nullable = false) // may want to allow nullable?
    public String getEventLocation() { return eventLocation; }

    public void setEventLocation(String eventLocation) { this.eventLocation = eventLocation; }

    @Basic
    @Column(name="start_date_time", nullable = false)
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

    @PreUpdate
    @PrePersist
    public void updateTimeStamps() {
        if (createdDateTime == null) {
            createdDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis());
        }
    }
}
