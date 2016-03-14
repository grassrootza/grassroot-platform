package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.GroupLogType;

import java.util.List;
import java.util.concurrent.Future;

/**
 * Created by luke on 2016/02/15.
 */
public interface AsyncGroupService {

    public void recordGroupLog(Long groupId, Long userDoingId, GroupLogType type, Long userOrGroupAffectedId, String description);

    public void assignDefaultLanguage(Group group, User user);

    public boolean hasDefaultLanguage(Group group);

    public Future<Group> addMembersWithoutRoles(Long groupId, List<User> newMembers);

}
