package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.services.GroupAccessControlManagementService;
import za.org.grassroot.services.PermissionsManagementService;
import za.org.grassroot.services.RoleManagementService;

import java.util.Set;

/**
 * Created by luke on 2016/01/14.
 * Wrapper class to make various properties about a user's membership in a group readily available to view
 */
public class MemberWrapper {

    private static final Logger log = LoggerFactory.getLogger(MemberWrapper.class);

    private User user;
    private Group group;
    private Role role;
    private String roleName;

    public MemberWrapper(User user, Group group, Role role) {
        log.info("Constructing member wrapper for user ... " + user.getId() + " ... in group ... " + group.getId());
        this.user = user;
        this.group = group;
        this.role = role;
        if (role == null)
            roleName = "NULL";
        else
            roleName = role.getName();
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


}
