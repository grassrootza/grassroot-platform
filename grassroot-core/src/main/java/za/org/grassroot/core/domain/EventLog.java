package za.org.grassroot.core.domain;

/**
 * Created by luke on 2015/07/16.
 * */

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.LocationHolder;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name="event_log")
public class EventLog implements TaskLog, LocationHolder {

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

    @Enumerated(EnumType.STRING)
    @Column(name="event_log_type", nullable = false, length = 50)
    private EventLogType eventLogType;

    @Enumerated(EnumType.STRING)
    @Column(name="response", length = 50)
    private EventRSVPResponse response;

    @Column(name = "start_time_changed")
    private Boolean startTimeChanged; // intended only for logs of type CHANGED

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Column(name = "location_source", nullable = true)
    private LocationSource locationSource;

    /*
    Constructors
     */

    private EventLog() {
        // for JPA
    }

    public EventLog(User user, Event event, EventLogType eventLogType, EventRSVPResponse response, Boolean startTimeChanged) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.user = user;
        this.event = Objects.requireNonNull(event);
        this.eventLogType = Objects.requireNonNull(eventLogType);
        this.response = response;
        this.startTimeChanged = startTimeChanged;
    }

    public EventLog(User user, Event event, EventLogType eventLogType, EventRSVPResponse response) {
        this(user, event, eventLogType, response, null);
    }

    public EventLog(User user, Event event, EventLogType eventLogType) {
        this(user, event, eventLogType, null);
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

    @Override
    public Task getTask() {
        return event;
    }

    public Event getEvent() {
        return event;
    }

    public EventLogType getEventLogType() {
        return eventLogType;
    }

    public EventRSVPResponse getResponse() {
        return response;
    }

    public boolean hasValidResponse() {
        return response != null && !response.equals(EventRSVPResponse.INVALID_RESPONSE);
    }

    public Boolean getStartTimeChanged() {
        return startTimeChanged;
    }

    public void setResponse(EventRSVPResponse response) {
        this.response = response;
    }

    public GeoLocation getLocation() {
        return location;
    }

    public void setLocation(GeoLocation location) {
        this.location = location;
    }

    public void setLocationSource(LocationSource locationSource) { this.locationSource = locationSource; }

    public void setLocationWithSource(GeoLocation location, LocationSource source) {
        this.location = location;
        this.locationSource = source;
    }

    public boolean hasLocation() { return location != null; }

    @Override
    public LocationSource getSource() {
        return locationSource; // by definition
    }

    @Override
    public String toString() {
        return "EventLog{" +
                "type=" + eventLogType +
                ", user=" + user +
                ", event=" + event +
                ", response='" + response + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", uid=" + uid +
                ", id=" + id +
                '}';
    }
}
