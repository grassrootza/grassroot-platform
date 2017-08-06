package za.org.grassroot.webapp.model.rest.wrappers;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

/**
 * Created by luke on 2016/05/06.
 */
public class MembershipResponseWrapper {

    private String memberUid;
    private String displayName;
    private String groupUid;
    private String phoneNumber;
    private String roleName;
    private boolean selected;

    public MembershipResponseWrapper(Group group, User user, Role role, boolean selected) {
        this.memberUid = user.getUid();
        this.displayName = group.getMembership(user).getDisplayName();
        this.groupUid = group.getUid();
        this.phoneNumber = user.getPhoneNumber();
        this.roleName = role.getName();
        this.selected = selected;
    }

    public MembershipResponseWrapper(User user) {
        this.memberUid = user.getUid();
        this.displayName = user.nameToDisplay();
        this.phoneNumber = user.getPhoneNumber();
    }

    public String getMemberUid() {
        return memberUid;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public String getRoleName() {
        return roleName;
    }

    public boolean isSelected() { return selected; }
}
