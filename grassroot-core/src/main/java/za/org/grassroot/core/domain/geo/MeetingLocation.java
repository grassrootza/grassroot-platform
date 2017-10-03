package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/03/28.
 * todo: create task Location and then inheritance (use multi table as these could get very long)
 */
@Entity
@Table(name = "meeting_location",
        uniqueConstraints = @UniqueConstraint(name = "uk_meeting_location_event_date", columnNames = {"event_id", "calculated_time"}))
public class MeetingLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_event_location_event"))
    private Meeting meeting;

    @Column(name = "calculated_time", nullable = false)
    private Instant calculatedDateTime;

    private GeoLocation location;

    @Column(name = "score", nullable = false)
    private float score;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private EventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 50, nullable = false)
    private LocationSource source;

    private MeetingLocation() {
        // for JPA
    }

    public MeetingLocation(Meeting meeting, GeoLocation location, float score, EventType eventType, LocationSource source) {
        Objects.requireNonNull(meeting);
        Objects.requireNonNull(location);
        Objects.requireNonNull(eventType);

        this.meeting = meeting;
        this.calculatedDateTime = Instant.now();
        this.location = location;
        this.score = score;
        this.eventType = eventType;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public Meeting getMeeting() {
        return meeting;
    }

    public Instant getCalculatedDateTime() {
        return calculatedDateTime;
    }

    public GeoLocation getLocation() {
        return location;
    }

    public float getScore() {
        return score;
    }

    public EventType getEventType() {
        return eventType;
    }

    public LocationSource getSource() { return source; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        MeetingLocation that = (MeetingLocation) o;

        if (!meeting.equals(that.meeting)) return false;
        return calculatedDateTime.equals(that.calculatedDateTime);
    }

    @Override
    public int hashCode() {
        int result = meeting.hashCode();
        result = 31 * result + calculatedDateTime.hashCode();
        return result;
    }
}