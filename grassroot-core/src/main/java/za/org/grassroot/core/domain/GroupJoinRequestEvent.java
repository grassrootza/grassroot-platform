package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.GroupJoinRequestEventType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents event that occurred on group join request.
 */
@Entity
@Table(name = "group_join_request_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_join_req_event_uid", columnNames = "uid"))
public class GroupJoinRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_join_req_event_user"))
    private User user;

    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private GroupJoinRequestEventType type;

    @Column(name = "occurrence_time", nullable = false)
    private Instant occurrenceTime;

    @ManyToOne
    @JoinColumn(name = "request_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_group_join_req_event_request"))
    private GroupJoinRequest request;

    private GroupJoinRequestEvent() {
        // for JPA
    }

    public GroupJoinRequestEvent(GroupJoinRequestEventType type, GroupJoinRequest request, User user, Instant occurrenceTime) {
        this.uid = UIDGenerator.generateId();
        this.type = Objects.requireNonNull(type);
        this.request = Objects.requireNonNull(request);
        this.user = Objects.requireNonNull(user);
        this.occurrenceTime = Objects.requireNonNull(occurrenceTime);
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public User getUser() {
        return user;
    }

    public GroupJoinRequestEventType getType() {
        return type;
    }

    public Instant getOccurrenceTime() {
        return occurrenceTime;
    }

    public GroupJoinRequest getRequest() { return request; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GroupJoinRequestEvent)) {
            return false;
        }

        GroupJoinRequestEvent that = (GroupJoinRequestEvent) o;

        if (getUid() != null ? !getUid().equals(that.getUid()) : that.getUid() != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("GroupJoinRequestEvent{");
        sb.append("type=").append(type);
        sb.append(", id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", user=").append(user);
        sb.append(", occurrenceTime=").append(occurrenceTime);
        sb.append('}');
        return sb.toString();
    }
}
