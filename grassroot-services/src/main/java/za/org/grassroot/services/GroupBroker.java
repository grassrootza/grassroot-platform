package za.org.grassroot.services;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface GroupBroker {

    Group load(String groupUid);

    /** METHODS FOR CREATING AND EDITING GROUPS **/

    Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
                 GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes);

    void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow);

    boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow);

    void updateName(String userUid, String groupUid, String name);

    void updateDescription(String userUid, String groupUid, String description);

    void updateGroupDefaultReminderSetting(String userUid, String groupUid, int reminderMinutes);

    void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups);

    /** METHODS FOR DEALING WITH MEMBERS AND PERMISSIONS **/

    void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed);

    void notifyOrganizersOfJoinCodeUse(Instant periodStart, Instant periodEnd);

    void removeMembers(String userUid, String groupUid, Set<String> memberUids);

    void unsubscribeMember(String userUid, String groupUid);

    void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName);

    void updateMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos);

    void updateGroupPermissions(String userUid, String groupUid, Map<String, Set<Permission>> newPermissions);

    /** METHODS FOR DEALING WITH JOIN TOKENS, PUBLIC SETTINGS, AND SEARCHING **/

    String openJoinToken(String userUid, String groupUid, boolean temporary, LocalDateTime expiryDateTime);

    void closeJoinToken(String userUid, String groupUid);

    void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber);

    List<Group> findPublicGroups(String searchTerm);

    Group findGroupFromJoinCode(String joinCode);

    /** METHODS FOR DEALING WITH SUBGROUPS, LINKING GROUPS, AND MERGING **/

    Set<Group> subGroups(String groupUid);

    List<Group> parentChain(String groupUid);

    List<GroupTreeDTO> groupTree(String userUid);

    Set<Group> possibleParents(String userUid, String groupUid);

    void link(String userUid, String childGroupUid, String parentGroupUid);

    Set<Group> mergeCandidates(String userUid, String groupUid);

    Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName);

    /** METHODS FOR RETRIEVING GROUP HISTORY & GROUP LOGS **/

    LocalDateTime getLastTimeGroupActiveOrModified(String groupUid);

    GroupLog getMostRecentLog(Group group);

    List<LocalDate> getMonthsGroupActive(String groupUid);

    List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd);

    // pass null to eventType to get all events
    // pass null to either of the timestamps to leave unlimited (i.e., all the way to future, or all the way to past
    List<Event> retrieveGroupEvents(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd);

}
