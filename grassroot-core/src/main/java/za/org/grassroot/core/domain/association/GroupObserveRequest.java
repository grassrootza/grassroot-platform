package za.org.grassroot.core.domain.association;

import org.hibernate.annotations.Type;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AssociationRequestType;

import javax.persistence.*;
import java.util.Objects;

/*
Request to 'observe' a group, which basically means to see its activity records & history, and create outside todos
 */
@Entity
@Table(name = "group_observe_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_grp_observe_request_uid", columnNames = "uid"))
public class GroupObserveRequest extends AbstractAssociationRequest<User, Group> implements TagHolder {

    @ManyToOne
    @JoinColumn(name = "observer_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_obs_requestor"))
    private User observer;

    @ManyToOne
    @JoinColumn(name = "group_id", nullable = false, foreignKey = @ForeignKey(name = "fk_group_obs_request_group"))
    private Group group;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    public GroupObserveRequest(User observer, Group group, String message, String[] tags) {
        super(message);
        this.observer= Objects.requireNonNull(observer);
        this.group = Objects.requireNonNull(group);
        this.tags = tags;
    }

    @Override
    public User getRequestor() {
        return observer;
    }

    @Override
    public Group getDestination() {
        return group;
    }

    @Override
    public AssociationRequestType getType() {
        return AssociationRequestType.GROUP_OBSERVE;
    }

    public String[] getTags() {
        return tags == null ? new String[0] : tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }
}
