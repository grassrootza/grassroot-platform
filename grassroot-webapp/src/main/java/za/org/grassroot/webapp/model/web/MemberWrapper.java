package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

/**
 * Created by luke on 2016/01/14.
 * Wrapper class to make various properties about a user's membership in a group readily available to view
 */
public class MemberWrapper {

    private static final Logger log = LoggerFactory.getLogger(MemberWrapper.class);

    // todo: strip these down to just names & UIDs, those being things only ever used (else getting lots for little)
    private User user;
    private Group group;
    private Role role;
    private String roleName;
    private boolean selected;

    public MemberWrapper(User user, Group group, Role role) {
        // log.info("Constructing member wrapper for user ... " + user.getId() + " ... in group ... " + group.getId());
        this.user = user;
        this.group = group;
        this.role = role;
        if (role == null)
            roleName = "NULL";
        else
            roleName = role.getName();
    }

    // todo: replace Group in here with the abstract, member container
    public MemberWrapper(User user, Group group, boolean selected) {
        this.user = user;
        this.group = group;
        this.role = group.getMembership(user).getRole();
        roleName = role.getName();
        this.selected = selected;
    }

    public MemberWrapper(User user, boolean selected) {
        this.user = user;
        this.selected = selected;
    }

    public MemberWrapper() {

    }

    public User getUser() {
        return user;
    }

    public Group getGroup() {
        return group;
    }

    public Role getRole() {
        return role;
    }

    public String getRoleName() { return roleName; }

    public void setRoleName() { this.roleName = roleName; }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        this.selected = selected;
    }
}
