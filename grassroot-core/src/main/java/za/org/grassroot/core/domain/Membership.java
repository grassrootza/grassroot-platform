package za.org.grassroot.core.domain;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

public class Membership {
    private Group group;
    private User user;
    private Role role;

    public Membership(Group group, User user, Role role) {
        this.group = group;
        this.user = user;
        this.role = role;
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
