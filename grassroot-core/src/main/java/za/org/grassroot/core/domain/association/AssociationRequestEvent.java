package za.org.grassroot.core.domain.association;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AssocRequestEventType;
import za.org.grassroot.core.enums.AssociationRequestType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

/**
 * Represents event that occurred on group join request.
 */
@Entity
@Table(name = "association_request_event",
        uniqueConstraints = @UniqueConstraint(name = "uk_assoc_req_event_uid", columnNames = "uid"),
        indexes = {@Index(name = "idx_assoc_req_event_req_uid", columnList = "request_uid")})
public class AssociationRequestEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_assoc_req_event_user"))
    private User user;

    @Column(name = "type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AssocRequestEventType type;

    @Column(name = "occurrence_time", nullable = false)
    private Instant occurrenceTime;

    @Column(name = "request_uid", length = 50, updatable = false, nullable = false)
    private String requestUid;

    @Column(name = "request_type", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private AssociationRequestType requestType;

    private AssociationRequestEvent() {
        // for JPA
    }

    public AssociationRequestEvent(AssocRequestEventType type, AbstractAssociationRequest request, User user, Instant occurrenceTime) {
        Objects.requireNonNull(request);

        this.uid = UIDGenerator.generateId();
        this.type = Objects.requireNonNull(type);
        this.requestUid = request.getUid();
        this.requestType = request.getType();
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

    public AssocRequestEventType getType() {
        return type;
    }

    public Instant getOccurrenceTime() {
        return occurrenceTime;
    }

    public String getRequestUid() { return requestUid; }

    public AssociationRequestType getRequestType() { return requestType; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AssociationRequestEvent)) {
            return false;
        }

        AssociationRequestEvent that = (AssociationRequestEvent) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("AssociationRequestEvent{");
        sb.append("type=").append(type);
        sb.append(", id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", user=").append(user);
        sb.append(", occurrenceTime=").append(occurrenceTime);
        sb.append('}');
        return sb.toString();
    }
}
