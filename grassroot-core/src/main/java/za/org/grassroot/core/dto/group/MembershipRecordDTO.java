package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.enums.GroupLogType;

@Getter
public class MembershipRecordDTO {

    final Long groupLogId;
    final String groupUid;
    final String userUid;
    final String memberName;
    final String roleName;
    final long changeDateTimeMillis;
    final GroupLogType changeType;
    final String changingUserName;
    final String description;

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
