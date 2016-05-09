package za.org.grassroot.webapp.model.rest.ResponseWrappers;

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

    public MembershipResponseWrapper(Group group, User user, Role role) {
        this.memberUid = user.getUid();
        this.displayName = user.nameToDisplay();
        this.groupUid = group.getUid();
        this.phoneNumber = user.getPhoneNumber();
        this.roleName = role.getName();
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
}
