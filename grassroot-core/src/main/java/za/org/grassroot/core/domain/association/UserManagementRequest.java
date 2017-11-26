package za.org.grassroot.core.domain.association;

import org.hibernate.annotations.Type;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.AssociationRequestType;

import javax.persistence.*;

/*
Allows one user to see the groups of another
 */
@Entity
@Table(name = "user_mgmt_request",
        uniqueConstraints = @UniqueConstraint(name = "uk_user_mgmt_request_uid", columnNames = "uid"))
public class UserManagementRequest extends AbstractAssociationRequest<User, User> implements TagHolder {

    @ManyToOne
    @JoinColumn(name = "manager_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_mgr_requestor"))
    private User manager;

    @ManyToOne
    @JoinColumn(name = "managed_user_id", nullable = false, foreignKey = @ForeignKey(name = "fk_user_mgr_managed"))
    private User user;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    private UserManagementRequest() {
        // for JPA
    }

    public UserManagementRequest(User user, User manager) {
        this.manager = manager;
        this.user = user;
        this.tags = new String[0];
    }

    @Override
    public User getRequestor() {
        return manager;
    }

    @Override
    public User getDestination() {
        return user;
    }

    @Override
    public AssociationRequestType getType() {
        return AssociationRequestType.USER_MANAGER;
    }

    public String[] getTags() {
        return tags == null ? new String[0] : tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }
}