package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.enums.EventType;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2017/03/28.
 */
@Entity
@Table(name = "event_location",
        uniqueConstraints = @UniqueConstraint(name = "uk_event_location_event_date", columnNames = {"event_id", "calculated_time"}))
public class EventLocation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "event_id", nullable = false, updatable = false, foreignKey = @ForeignKey(name = "fk_event_location_event"))
    private Event event;

    @Column(name = "calculated_time", nullable = false)
    private Instant calculatedDateTime;

    private GeoLocation location;

    @Column(name = "score", nullable = false)
    private float score;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private EventType eventType;

    private EventLocation() {
        // for JPA
    }

    public EventLocation(Event event, GeoLocation location, float score, EventType eventType) {
        Objects.requireNonNull(event);
        Objects.requireNonNull(location);
        Objects.requireNonNull(eventType);

        this.event = event;
        this.calculatedDateTime = Instant.now();
        this.location = location;
        this.score = score;
        this.eventType = eventType;
    }

    public Long getId() {
        return id;
    }

    public Event getEvent() {
        return event;
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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        EventLocation that = (EventLocation) o;

        if (!event.equals(that.event)) return false;
        return calculatedDateTime.equals(that.calculatedDateTime);
    }

    @Override
    public int hashCode() {
        int result = event.hashCode();
        result = 31 * result + calculatedDateTime.hashCode();
        return result;
    }
}
