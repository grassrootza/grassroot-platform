package za.org.grassroot.services.group;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.*;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.enums.GroupViewPriority;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.LocalDateTime;
import java.util.*;

public interface GroupBroker {

    Group load(String groupUid);

    Group checkForDuplicate(String userUid, String groupName);

    /** METHODS FOR CREATING AND EDITING GROUPS **/

    Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
				 GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes,
				 boolean openJoinToken, boolean discoverable, boolean addToAccountIfPresent);

    void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow);

    boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow);

    void updateName(String userUid, String groupUid, String name);

    void updateDescription(String userUid, String groupUid, String description);

    void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups);

    void updateTopics(String userUid, String groupUid, Set<String> topics);

    // these are a special set of topics that are presented to the user
    void setJoinTopics(String userUid, String groupUid, List<String> joinTopics);

    /** METHODS FOR DEALING WITH MEMBERS AND PERMISSIONS **/

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos,
                    GroupJoinMethod joinMethod, boolean adminUserCalling);

    void addMembersToSubgroup(String userUid, String groupUid, String subGroupUid, Set<String> memberUids);

    void deactivateSubGroup(String userUid, String parentUid, String subGroupUid);

    void renameSubGroup(String userUid, String parentUid, String subGroupUid, String newName);

    void asyncMemberToSubgroupAdd(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    void copyAllMembersIntoGroup(String userUid, String fromGroupUid, String toGroupUid, boolean keepTopics, String addTopic);

    Membership addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed, UserInterfaceType interfaceType);

    Membership addMemberViaJoinCode(User user, Group group, String tokenPassed, UserInterfaceType interfaceType);

    String addMemberViaJoinPage(String groupUid, String code, String broadcastId, String userUid, String name, String phone, String email,
                                Province province, Locale language, List<String> topics, UserInterfaceType interfaceType);

    void setMemberJoinTopics(String userUid, String groupUid, String memberUid, List<String> joinTopics);

    void asyncAddMemberships(String initiatorUid, String groupUid, Set<MembershipInfo> membershipInfos,
                             GroupJoinMethod joinMethod, String joinMethodDescriptor,
                             boolean duringGroupCreation, boolean createWelcomeNotifications);

    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void removeMembersFromSubgroup(String userUid, String parentUid, String childUid, Set<String> memberUids);

    void unsubscribeMember(String userUid, String groupUid);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, GroupRole roleName);

    void updateMembershipDetails(String userUid, String groupUid, String memberUid, String name, String phone, String email,
                                 Province province);

    void assignMembershipTopics(String userUid, String groupUid, boolean allMembers, Set<String> memberUids, Set<String> topics, boolean preservePrior);

    void removeTopicFromMembers(String userUid, String groupUid, Collection<String> topic, boolean allMembers, Set<String> memberUids);

    void alterMemberTopicsTeamsOrgs(String userUid, String groupUid, String memberUid, Set<String> affiliations, Set<String> taskTeams, Set<String> topics);

    @Transactional
    boolean setGroupPinnedForUser(String userUid, String groupUid, boolean pinned);

    boolean updateViewPriority(String userUid, String groupUid, GroupViewPriority priority);

    void updateGroupPermissionsForRole(String userUid, String groupUid, GroupRole roleName, Set<Permission> permissionsToAdd,
									   Set<Permission> permissionsToRemove);

    void updateMemberAlias(String userUid, String groupUid, String alias);

    /*
    Method for conducting several edits at once, principally if those edits are done offline and sent as a bundle in a queue
    Passing null (or reference set) to any argument will just cause it to be skipped, as will passing the present value
     */
    void combinedEdits(String userUid, String groupUid, String groupName, String description, boolean resetToDefaultImage, GroupDefaultImage defaultImage,
                       boolean isPublic, boolean toCloseJoinCode, Set<String> membersToRemove, Set<String> organizersToAdd, int reminderMinutes);

    /** METHODS FOR DEALING WITH JOIN TOKENS, PUBLIC SETTINGS, AND SEARCHING **/

    Group loadAndRecordUse(String groupUid, String code, String broadcastId);

    String openJoinToken(String userUid, String groupUid, LocalDateTime expiryDateTime);

    void closeJoinToken(String userUid, String groupUid);

    // url to shorten should be _whole_ url, with path/query params etc set as frontend wants (this is view's job to decide,
    // since view will be handling the incoming requests)
    GroupJoinCode addJoinTag(String userUid, String groupUid, String tag, String urlToShorten);

    void removeJoinTag(String userUid, String groupUid, String tag);

    Group searchForGroupByWord(String userUid, String phrase);

    Set<String> getUsedJoinWords();

    Map<String, String> getJoinWordsWithGroupIds();

    void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber);

    /** METHODS FOR DEALING WITH SUBGROUPS, LINKING GROUPS, AND MERGING **/

    void addMemberViaCampaign(User user, Group hgroup, String campaignCode);

    void sendGroupJoinCodeNotification(String userUid, String groupUid);

    void sendAllGroupJoinCodesNotification(String userUid);
}
