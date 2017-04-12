package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.domain.UidIdentifiable;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.TaskType;

import javax.persistence.*;
import java.time.Instant;

/**
 * Created by luke on 2017/04/11.
 */
@Entity
@Inheritance(strategy = InheritanceType.TABLE_PER_CLASS)
public abstract class TaskLocation<P extends UidIdentifiable> {

    @Column(name = "calculated_time", nullable = false)
    private Instant calculatedDateTime;

    private GeoLocation location;

    @Column(name = "score", nullable = false)
    private float score;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", length = 50, nullable = false)
    private TaskType taskType;

    @Column(name = "source", nullable = false)
    @Enumerated(EnumType.STRING)
    private LocationSource source;

}
