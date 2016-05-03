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

    @Column(name="created_date_time", nullable = false, updatable = false)
    private Instant createdDateTime;

    @ManyToOne
    @JoinColumn(name = "user_id")
    private User user; // user can be null to represent the app itself as initiator (eg. scheduled action)

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Enumerated
    private EventLogType eventLogType;

    @Column
    private String message;

    /*
    Constructors
     */

    private EventLog() {
        // for JPA
    }

    public EventLog(User user, Event event, EventLogType eventLogType) {
        this(user, event, eventLogType, null);
    }

    public EventLog(User user, Event event, EventLogType eventLogType, String message) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.user = user;
        this.event = Objects.requireNonNull(event);
        this.eventLogType = Objects.requireNonNull(eventLogType);
        this.message = message;
    }

    public Long getId() {
        return id;
    }

    public String getUid() { return uid; }

    public Instant getCreatedDateTime() {
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
                ", rype=" + eventLogType +
                ", user=" + user +
                ", event=" + event +
                ", message='" + message + '\'' +
                ", createdDateTime=" + createdDateTime +
                '}';
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
