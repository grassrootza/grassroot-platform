package za.org.grassroot.core.domain;


import za.org.grassroot.core.enums.GroupJoinRequestStatus;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Request for joining the group.
 * We currently don't have child events here, because every collection association is potential burden,
 * and since it is planned to be rarely needed, we only define their relationship from event side.
 */
@Entity
@Table(name = "group_join_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_join_request_uid", columnNames = "uid"))
public class GroupJoinRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @ManyToOne
    @JoinColumn(name = "requestor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_join_requestor"))
    private User requestor;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_join_request_group"))
    private Group group;

    @Column(name = "creation_time", nullable = false)
    private Instant creationTime;

    @Column(name = "processed_time")
    private Instant processedTime;

    @Column(name = "status", nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private GroupJoinRequestStatus status;

    private GroupJoinRequest() {
        // for JPA
    }

    public GroupJoinRequest(User requestor, Group group, Instant creationTime) {
        this.uid = UIDGenerator.generateId();
        this.requestor = Objects.requireNonNull(requestor);
        this.group = Objects.requireNonNull(group);
        this.creationTime = Objects.requireNonNull(creationTime);
        this.status = GroupJoinRequestStatus.PENDING;
    }

    public Long getId() {
        return id;
    }

    public String getUid() {
        return uid;
    }

    public User getRequestor() {
        return requestor;
    }

    public Group getGroup() {
        return group;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public GroupJoinRequestStatus getStatus() {
        return status;
    }

    public Instant getProcessedTime() {
        return processedTime;
    }

    public void setProcessedTime(Instant processedTime) {
        this.processedTime = processedTime;
    }

    public void setStatus(GroupJoinRequestStatus status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof GroupJoinRequest)) {
            return false;
        }

        GroupJoinRequest that = (GroupJoinRequest) o;

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
        final StringBuilder sb = new StringBuilder("GroupJoinRequest{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", status=").append(status);
        sb.append(", requestorId=").append(requestor.getId());
        sb.append(", groupId=").append(group.getId());
        sb.append('}');
        return sb.toString();
    }
}
