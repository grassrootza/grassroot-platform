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

import javax.persistence.*;
import java.sql.Timestamp;
import java.util.Calendar;

@Entity
@Table(name="event_log")
public class EventLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    @ManyToOne
    private User user;

    @ManyToOne
    private Event event;

    @Basic
    @Column
    private int type;

    @Basic
    @Column
    private String message;

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


    public EventLog() {
    }

    public EventLog(User user, Event event, int type, String message) {
        this.user = user;
        this.event = event;
        this.type = type;
        this.message = message;
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

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public int getType() {
        return type;
    }

    public void setType(int type) {
        this.type = type;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    @Override
    public String toString() {
        return "EventLog{" +
                "id=" + id +
                ", createdDateTime=" + createdDateTime +
                ", user=" + user +
                ", event=" + event +
                ", type=" + type +
                ", message='" + message + '\'' +
                '}';
    }
}
