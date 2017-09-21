package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "group_user_membership",
        uniqueConstraints = @UniqueConstraint(name = "uk_membership_group_user", columnNames = {"group_id", "user_id"}))
public class Membership implements Serializable {
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

    @ManyToOne(optional = false, cascade = CascadeType.PERSIST)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(name = "join_time", nullable = false)
    private Instant joinTime;

    @Basic
    @Column(name = "alias", length = 50, nullable = true)
    private String alias;

    private Membership() {
        // for JPA
    }

    public Membership(Group group, User user, Role role, Instant joinTime) {
        this.group = Objects.requireNonNull(group);
        this.user = Objects.requireNonNull(user);
        this.role = Objects.requireNonNull(role);
        this.joinTime = Objects.requireNonNull(joinTime);
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

    public Instant getJoinTime() {
        return joinTime;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }

    public String getDisplayName() {
        return alias == null || alias.trim().isEmpty() ? user.getDisplayName() : alias;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Membership)) {
            return false;
        }

        Membership that = (Membership) o;

        if (getGroup() != null ? !getGroup().equals(that.getGroup()) : that.getGroup() != null) {
            return false;
        }

        return getUser() != null ? getUser().equals(that.getUser()) : that.getUser() == null;

    }

    @Override
    public int hashCode() {
        int result = getGroup() != null ? getGroup().hashCode() : 0;
        result = 31 * result + (getUser() != null ? getUser().hashCode() : 0);
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
