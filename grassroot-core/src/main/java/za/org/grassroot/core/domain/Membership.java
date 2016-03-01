package za.org.grassroot.core.domain;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

import javax.persistence.*;
import java.util.Objects;

@Entity
@Table(name = "group_user_membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_group_user", columnNames = {"group_id", "user_id"}))
public class Membership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "group_id", nullable = false)
    private Group group;

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    private Membership() {
        // for JPA
    }

    public Membership(Group group, User user, Role role) {
        this.group = Objects.requireNonNull(group);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
    }

    public Long getId() {
        return id;
    }

    public Group getGroup() {
        return group;
    }

    public User getUser() {
        return user;
    }

    public Role getRole() {
        return role;
    }

    public void setRole(Role role) {
        this.role = Objects.requireNonNull(role);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        Membership that = (Membership) o;

        if (!group.equals(that.group)) {
            return false;
        }
        if (!user.equals(that.user)) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = group.hashCode();
        result = 31 * result + user.hashCode();
        return result;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Membership{");
        sb.append("group=").append(group);
        sb.append(", user=").append(user);
        sb.append(", role=").append(role);
        sb.append('}');
        return sb.toString();
    }
}
