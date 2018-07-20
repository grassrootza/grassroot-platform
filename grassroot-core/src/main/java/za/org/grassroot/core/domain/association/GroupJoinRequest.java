package za.org.grassroot.core.domain.association;


import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.AssociationRequestType;

import javax.persistence.*;
import java.util.Objects;

/**
 * Request for joining the group.
 * We currently don't have child events here, because every collection association is potential burden,
 * and since it is planned to be rarely needed, we only define their relationship from event side.
 */
@Entity
@Table(name = "group_join_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_group_join_request_uid", columnNames = "uid"))
public class GroupJoinRequest extends AbstractAssociationRequest<User,Group> {

    @ManyToOne
    @JoinColumn(name = "requestor_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_join_requestor"))
    private User requestor;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_join_request_group"))
    private Group group;

    private GroupJoinRequest() {
        // for JPA
    }

    public GroupJoinRequest(User requestor, Group group, String description) {
        super(description);
        this.requestor = Objects.requireNonNull(requestor);
        this.group = Objects.requireNonNull(group);
    }

    public User getRequestor() {
        return requestor;
    }

    public Group getGroup() {
        return group;
    }

    public Group getDestination() {
        return group;
    }

    public AssociationRequestType getType() { return AssociationRequestType.GROUP_JOIN; }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof GroupJoinRequest)) {
            return false;
        }

        GroupJoinRequest that = (GroupJoinRequest) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;
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
