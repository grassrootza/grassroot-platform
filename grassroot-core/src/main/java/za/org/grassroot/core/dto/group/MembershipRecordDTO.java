package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.enums.GroupLogType;

@Getter
public class MembershipRecordDTO {

    private final Long groupLogId;
    private final String groupUid;
    private final String userUid;
    private final String memberName;
    private final String roleName;
    private final long changeDateTimeMillis;
    private final GroupLogType changeType;
    private final String changingUserName;
    private final String description;

    public MembershipRecordDTO(Membership membership, GroupLog groupLog) {
        this.groupLogId = groupLog.getId(); // since no UID on group log ...
        this.groupUid = groupLog.getGroup().getUid();
        this.userUid = membership != null ? membership.getUser().getUid() :
                groupLog.getTarget().getUid();
        this.memberName = membership != null ? membership.getDisplayName() :
                groupLog.getUserNameSafe();
        this.roleName = membership != null ? membership.getRole().getName() : null;
        this.changeDateTimeMillis = groupLog.getCreatedDateTime().toEpochMilli();
        this.changeType = groupLog.getGroupLogType();
        this.changingUserName = groupLog.getUser().getName();
        this.description = groupLog.getDescription();
    }

}
