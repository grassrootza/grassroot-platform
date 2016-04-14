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
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Calendar;
import java.util.Objects;

@Entity
@Table(name="event_log")
public class EventLog {

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

    @Basic
    @Column
    private String message;

    @Basic
    @Column(name = "message_default", insertable = true, nullable = false)
    private UserMessagingPreference defaultMessageType;

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

    public EventLog(User user, Event event, EventLogType eventLogType, String message, UserMessagingPreference defaultMessageType) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(event);
        Objects.requireNonNull(eventLogType);

        this.uid = UIDGenerator.generateId();
        this.user = user;
        this.event = event;
        this.eventLogType = eventLogType;
        this.message = message;
        this.createdDateTime = Timestamp.from(Instant.now());
        if (defaultMessageType == null)
            this.defaultMessageType = user.getMessagingPreference();
        else
            this.defaultMessageType = defaultMessageType;
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

    public void setUser(User user) {
        this.user = user;
    }

    public Event getEvent() {
        return event;
    }

    public void setEvent(Event event) {
        this.event = event;
    }

    public EventLogType getEventLogType() {
        return eventLogType;
    }

    public void setEventLogType(EventLogType eventLogType) {
        this.eventLogType = eventLogType;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public UserMessagingPreference getDefaultMessageType() { return defaultMessageType; }

    public void setDefaultMessageType(UserMessagingPreference defaultMessageType) { this.defaultMessageType = defaultMessageType; }

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
                ", messageType=" + defaultMessageType +
                '}';
    }
}
