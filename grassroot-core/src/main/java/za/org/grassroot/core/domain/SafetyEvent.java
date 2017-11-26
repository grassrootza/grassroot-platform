package za.org.grassroot.core.domain;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by paballo on 2016/07/18.
 */
@Entity @Getter
@Table(name = "safety_event")
public class SafetyEvent implements EntityForUserResponse {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;


    @Column(name = "created_date_time", nullable = false, insertable = true, updatable = false)
    private Instant createdDateTime;

    @Column(name = "scheduled_reminder_time")
    @Setter private Instant scheduledReminderTime;

    @ManyToOne()
    @JoinColumn(name = "activated_by_user", nullable = false, updatable = false)
    private User activatedBy;

    @ManyToOne()
    @JoinColumn(name = "group_id", nullable = false, updatable = false)
    @Setter private Group group;

    @Column(name = "active")
    @Setter private boolean active;

    @Column(name = "false_alarm")
    @Setter private boolean falseAlarm;

    @Column(name = "responded_to")
    @Setter private boolean respondedTo;

    @Embedded
    @AttributeOverrides({
            @AttributeOverride(name="latitude", column = @Column(nullable = true)),
            @AttributeOverride(name="longitude", column = @Column(nullable = true))
    })
    private GeoLocation location;

    @Enumerated(EnumType.STRING)
    @Column(name = "location_source", length = 50, nullable = true)
    private LocationSource locationSource;

    private SafetyEvent() {
    }

    public SafetyEvent(User activatedBy, Group parentGroup) {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.activatedBy = activatedBy;
        this.group = parentGroup;
        this.active =true;
        //send a reminder after 20 minutes
        this.scheduledReminderTime = createdDateTime.plus(20, ChronoUnit.MINUTES);
    }

    @Override
    public JpaEntityType getJpaEntityType() {
        return JpaEntityType.SAFETY;
    }

    @Override
    public String getName() {
        return activatedBy.getName();
    }

    @Override
    public String getDescription() {
        return "";
    }

    @Override
    public boolean hasName() {
        return true;
    }

    @Override
    public Set<User> getMembers() {
        return new HashSet<>();
    }

    @Override
    public Group getThisOrAncestorGroup() {
        return group;
    }

    public void updateScheduledReminderTime() {
        this.scheduledReminderTime = scheduledReminderTime.plus(20, ChronoUnit.MINUTES);
    }

    @Override
    public String toString() {
        return "SafetyEvent{" +
                "id=" + id +
                ", uid='" + uid + '\'' +
                ", createdDateTime=" + createdDateTime +
                ", group=" + group +
                '}';
    }
}
