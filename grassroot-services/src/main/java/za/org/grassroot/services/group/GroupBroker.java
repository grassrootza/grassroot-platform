package za.org.grassroot.services.group;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupDefaultImage;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;

public interface GroupBroker {

    Group load(String groupUid);

    Group checkForDuplicate(String userUid, String groupName);

    /** METHODS FOR CREATING AND EDITING GROUPS **/

    Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
                 GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes, boolean openJoinToken);

    void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow);

    boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow);

    void updateName(String userUid, String groupUid, String name);

    void updateDescription(String userUid, String groupUid, String description);

    void updateGroupDefaultReminderSetting(String userUid, String groupUid, int reminderMinutes);

    void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups);

    /** METHODS FOR DEALING WITH MEMBERS AND PERMISSIONS **/

    boolean canAddMember(String groupUid);

    int numberMembersBeforeLimit(String groupUid);

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos, boolean adminUserCalling);

    void copyMembersIntoGroup(String userUid, String groupUid, Set<String> memberUids);

    void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed);

    void notifyOrganizersOfJoinCodeUse(Instant periodStart, Instant periodEnd);

    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void unsubscribeMember(String userUid, String groupUid);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName);

    void updateMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos, boolean checkForDeletion);

    void updateGroupPermissions(String userUid, String groupUid, Map<String, Set<Permission>> newPermissions);

    void updateGroupPermissionsForRole(String userUid, String groupUid, String roleName, Set<Permission> permissionsToAdd,
                                       Set<Permission> permissionsToRemove);

    void updateMemberAlias(String userUid, String groupUid, String alias);

    /*
    Method for conducting several edits at once, principally if those edits are done offline and sent as a bundle in a queue
    Passing null (or reference set) to any argument will just cause it to be skipped, as will passing the present value
     */
    void combinedEdits(String userUid, String groupUid, String groupName, String description, boolean resetToDefaultImage, GroupDefaultImage defaultImage,
                       boolean isPublic, boolean toCloseJoinCode, Set<String> membersToRemove, Set<String> organizersToAdd);

    /** METHODS FOR DEALING WITH JOIN TOKENS, PUBLIC SETTINGS, AND SEARCHING **/

    String openJoinToken(String userUid, String groupUid, LocalDateTime expiryDateTime);

    void closeJoinToken(String userUid, String groupUid);

    void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber);

    /** METHODS FOR DEALING WITH SUBGROUPS, LINKING GROUPS, AND MERGING **/ // major todo: move to paid group (since will migrate to paid account feature)

    void link(String userUid, String childGroupUid, String parentGroupUid);

    Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName);

}
