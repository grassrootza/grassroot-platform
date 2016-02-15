package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupLogType;

/**
 * Created by luke on 2016/02/15.
 */
public interface AsyncGroupService {

    public void recordGroupLog(Long groupId, Long userDoingId, GroupLogType type, Long userOrGroupAffectedId, String description);

    public void wireNewGroupMemberLogsRoles(Group group, User newMember, Long addingUserId, boolean addDefaultRole);

    public void removeGroupMemberLogsRoles(Group group, User oldMember, User removingUser);

    public void assignDefaultLanguage(Group group, User user);

    public boolean hasDefaultLanguage(Group group);

}
