package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.Membership;

public class MembershipDTO extends MembershipInfo {

    @Getter private final String memberUid;
    @Getter private final String groupUid;
    @Getter private final String compositeUid;
    @Getter private final long joinedDateTimeMillis;

    public MembershipDTO(Membership membership) {
        super(membership.getUser(), membership.getDisplayName(), membership.getRole().getName(), null);
        this.memberUid = membership.getUser().getUid();
        this.userSetName = membership.getUser().isHasSetOwnName();
        this.groupUid = membership.getGroup().getUid();
        this.compositeUid = this.groupUid + "-" + this.memberUid;
        this.joinedDateTimeMillis = membership.getJoinTime().toEpochMilli();
    }
}
