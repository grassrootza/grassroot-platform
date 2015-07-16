package za.org.grassroot.meeting_organizer.model;

/**
 * Created by luke on 2015/07/16.
 *
 * Major to-do: Add relationship to user who created event
 * Major to-do: Add relationship to group that is participating in event
 * Major to-do: Construct logic for equals (non-trivial, as same group may have two events at same time ...)
 * Other: All to-dos as for User class
 */

import java.sql.Timestamp;
import javax.persistence.Basic;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;

@Entity
@Table(name="event")
public class Event {
    private String eventLocation;
    private Integer id;
    private Timestamp createdDateTime;
    private Timestamp eventStartDateTime;

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
    @Column(name="created_date_time", insertable = false, updatable = false)
    public Timestamp getCreatedDateTime() { return createdDateTime; }

    public void setCreatedDateTime(Timestamp createdDateTime) { this.createdDateTime = createdDateTime; }
}
