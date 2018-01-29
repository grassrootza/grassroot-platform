package za.org.grassroot.services.group;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupViewPriority;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupBroker {

    Group load(String groupUid);

    Group checkForDuplicate(String userUid, String groupName);

    /** METHODS FOR CREATING AND EDITING GROUPS **/

    Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
                 GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes,
                 boolean openJoinToken, boolean discoverable);

    void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow);

    boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow);

    void updateName(String userUid, String groupUid, String name);

    void updateDescription(String userUid, String groupUid, String description);

    void updateGroupDefaultReminderSetting(String userUid, String groupUid, int reminderMinutes);

    void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups);

    void updateTopics(String userUid, String groupUid, Set<String> topics);

    /** METHODS FOR DEALING WITH MEMBERS AND PERMISSIONS **/

    boolean canAddMember(String groupUid);

    int numberMembersBeforeLimit(String groupUid);

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos,
                    GroupJoinMethod joinMethod, boolean adminUserCalling);

    void copyMembersIntoGroup(String userUid, String groupUid, Set<String> memberUids);

    void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed, UserInterfaceType interfaceType);

    String addMemberViaJoinPage(String groupUid, String code, String userUid, String name, String phone, String email,
                                Province province, List<String> topics, UserInterfaceType interfaceType);

    void notifyOrganizersOfJoinCodeUse(Instant periodStart, Instant periodEnd);

    void asyncAddMemberships(String initiatorUid, String groupUid, Set<MembershipInfo> membershipInfos,
                             GroupJoinMethod joinMethod, String joinMethodDescriptor,
                             boolean duringGroupCreation, boolean createWelcomeNotifications);


    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void unsubscribeMember(String userUid, String groupUid);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName);

    void updateMembershipDetails(String userUid, String groupUid, String memberUid, String name, String phone, String email,
                                 Province province);

    // note: only accepts topics that are from the group itself
    void assignMembershipTopics(String userUid, String groupUid, String memberUid, Set<String> topics);

    @Transactional
    boolean setGroupPinnedForUser(String userUid, String groupUid, boolean pinned);

    boolean updateViewPriority(String userUid, String groupUid, GroupViewPriority priority);

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
                       boolean isPublic, boolean toCloseJoinCode, Set<String> membersToRemove, Set<String> organizersToAdd, int reminderMinutes);

    /** METHODS FOR DEALING WITH JOIN TOKENS, PUBLIC SETTINGS, AND SEARCHING **/

    Group loadAndRecordUse(String groupUid, String code);

    String openJoinToken(String userUid, String groupUid, LocalDateTime expiryDateTime);

    void closeJoinToken(String userUid, String groupUid);

    // url to shorten should be _whole_ url, with path/query params etc set as frontend wants (this is view's job to decide,
    // since view will be handling the incoming requests)
    GroupJoinCode addJoinTag(String userUid, String groupUid, String tag, String urlToShorten);

    void removeJoinTag(String userUid, String groupUid, String tag);

    Set<String> getUsedJoinWords();

    Map<String, String> getJoinWordsWithGroupIds();

    void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber);

    /** METHODS FOR DEALING WITH SUBGROUPS, LINKING GROUPS, AND MERGING **/

    void link(String userUid, String childGroupUid, String parentGroupUid);

    Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName);

    void addMemberViaCampaign(String userUidToAdd, String groupUid,String campaignCode);

    void sendGroupJoinCodeNotification(String userUid, String groupUid);

    void sendAllGroupJoinCodesNotification(String userUid);
}
