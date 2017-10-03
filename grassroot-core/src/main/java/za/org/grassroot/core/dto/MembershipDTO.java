package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Membership;

public class MembershipDTO extends MembershipInfo {

    private final String memberUid;

    public MembershipDTO(Membership membership) {
        super(membership.getUser(), membership.getDisplayName(), membership.getRole().getName());
        this.memberUid = membership.getUser().getUid();
        this.userSetName = membership.getUser().isHasSetOwnName();
    }

    public String getMemberUid() {
        return memberUid;
    }
}
