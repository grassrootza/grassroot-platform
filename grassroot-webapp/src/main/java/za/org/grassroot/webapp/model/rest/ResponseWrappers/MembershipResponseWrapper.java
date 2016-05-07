package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.domain.User;

/**
 * Created by luke on 2016/05/06.
 */
public class MembershipResponseWrapper {

    private String memberUid;
    private String memberDisplayName;
    private String groupUid;
    private String memberPhoneNumber;
    private String memberRoleName;

    public MembershipResponseWrapper(Group group, User user, Role role) {
        this.memberUid = user.getUid();
        this.memberDisplayName = user.nameToDisplay();
        this.groupUid = group.getUid();
        this.memberPhoneNumber = user.getPhoneNumber();
        this.memberRoleName = role.getName();
    }

    public String getMemberUid() {
        return memberUid;
    }

    public String getMemberDisplayName() {
        return memberDisplayName;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getMemberPhoneNumber() {
        return memberPhoneNumber;
    }

    public String getMemberRoleName() {
        return memberRoleName;
    }
}
