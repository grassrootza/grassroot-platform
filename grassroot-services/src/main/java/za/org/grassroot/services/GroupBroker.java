package za.org.grassroot.services;

import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.Set;

public interface GroupBroker {

    String create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos, GroupPermissionTemplate groupPermissionTemplate);

    void update(String userUid, String groupUid, String name);

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName);
}
