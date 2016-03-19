package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupBroker {

    Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos, GroupPermissionTemplate groupPermissionTemplate);

    void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow);

    boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow);

    void updateName(String userUid, String groupUid, String name);

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed);

    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void unsubscribeMember(String userUid, String groupUid);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName);

    void updateMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName);

    List<Group> findPublicGroups(String searchTerm);

    void updateGroupPermissions(String userUid, String groupUid, Map<String, Set<Permission>> newPermissions);

}
