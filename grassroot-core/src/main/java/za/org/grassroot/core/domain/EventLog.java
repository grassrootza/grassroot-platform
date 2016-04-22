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

import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;

@Entity
@Table(name="event_log")
public class EventLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name="created_date_time", insertable = true, updatable = false)
    private Timestamp createdDateTime;

    @ManyToOne
    private User user;

    @ManyToOne
    private Event event;

    @Enumerated
    private EventLogType eventLogType;

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

    private EventLog() {
        // for JPA
    }

    public EventLog(User user, Event event, EventLogType eventLogType, String message) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(event);
        Objects.requireNonNull(eventLogType);

        this.uid = UIDGenerator.generateId();
        this.user = user;
        this.event = event;
        this.eventLogType = eventLogType;
        this.message = message;
        this.createdDateTime = Timestamp.from(Instant.now());
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public User getUser() {
        return user;
    }

    public Event getEvent() {
        return event;
    }

    public EventLogType getEventLogType() {
        return eventLogType;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "EventLog{" +
                "id=" + id +
                ", uid=" + uid +
                ", createdDateTime=" + createdDateTime +
                ", user=" + user +
                ", event=" + event +
                ", eventLogType=" + eventLogType +
                ", message='" + message + '\'' +
                '}';
    }
}
