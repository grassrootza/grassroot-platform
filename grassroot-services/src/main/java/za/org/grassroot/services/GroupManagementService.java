package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupTreeDTO;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    /*
    Methods to create, save and name groups
     */

    public Group createNewGroup(User creatingUser, String groupName);

    public Group createNewGroup(User creatingUser, List<String> phoneNumbers);

    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers);

    public Group createNewGroupWithCreatorAsMember(User creatingUser, String groupName);

    public Group saveGroup(Group groupToSave, boolean createGroupLog, String description, Long changedByUserId);

    public Group renameGroup(Group group, String newGroupName);

    public Group renameGroup(Long groupId, String newGroupName);


    /*
    Methods to load and find groups
    todo: clean up redundancy
    */

    @PreAuthorize("hasPermission(#id, 'za.org.grassroot.core.domain.Group', 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS')")
    public Group secureLoadGroup(Long id);

    public Group loadGroup(Long groupId);

    public String getGroupName(Long groupId);

    public List<Group> getGroupsFromUser(User sessionUser);

    public List<Group> getCreatedGroups(User creatingUser);

    public List<Group> getGroupsPartOf(User sessionUser);

    public List<Group> getActiveGroupsPartOf(User sessionUser);

    public List<Group> getActiveGroupsPartOfOrdered(User sessionUser);

    public List<Group> getActiveGroupsPartOf(Long userId);

    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize);

    public List<Group> getListGroupsFromLogbooks(List<LogBook> logBooks);

    public List<Group> findDiscoverableGroups(String groupName);

    /*
    Methods to find, and add group members
     */

    public boolean isUserInGroup(Group group, User user);

    public Group addGroupMember(Group currentGroup, User newMember);

    public Group addGroupMemberWithDefaultRole(Group group, User user, String roleName);

    public Group addGroupMember(Long currentGroupId, Long newMemberId);

    public Group removeGroupMember(Group group, User user);

    public Group removeGroupMember(Long groupId, User user);

    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList);

    public Group addNumberToGroup(Long groupId, String phoneNumber);

    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers);

    public Group unsubscribeMember(Group group, User user);

    /*
    Methods to find if a user has an outstanding group management action to perform or groups on which they can perform it
     */

    public Group getLastCreatedGroup(User creatingUser);

    public boolean needsToRenameGroup(User sessionUser);

    public Group groupToRename(User sessionUser);

    public boolean canGroupBeSetInactive(Group group, User user);

    public boolean hasActiveGroupsPartOf(User user);

    public boolean canUserMakeGroupInactive(User user, Group group);

    public boolean canUserMakeGroupInactive(User user, Long groupId);

    public boolean isGroupCreatedByUser(Long groupId, User user);

    public List<Group> groupsOnWhichCanCallVote(User user);

    /*
    Methods to work with group joining tokens and group discovery
     */

    public Group getGroupByToken(String groupToken);

    public Group generateGroupToken(Long groupId);

    public Group generateGroupToken(Group group);

    public Group generateGroupToken(Group group, Integer daysValid);

    public Group generateGroupToken(Long groupId, Integer daysValid);

    public Group extendGroupToken(Group group, Integer daysExtension);

    public Group invalidateGroupToken(Group group);

    public Group invalidateGroupToken(Long groupId);

    public boolean groupHasValidToken(Long groupId);

    public boolean groupHasValidToken(Group group);

    public boolean tokenExists(String groupToken);

    // @PreAuthorize("hasPermission(#groupId, 'za.org.grassroot.core.domain.Group', 'GROUP_PERMISSION_UPDATE_GROUP_DETAILS')")
    public Group setGroupDiscoverable(Long groupId, boolean discoverable, User user);

    public Group setGroupDiscoverable(Group group, boolean discoverable, Long userId);

    public boolean canUserModifyGroup(Group group, User user);

    /*
    Methods do deal with sub groups and parent groups
     */

    public Group createSubGroup(User createdByUser, Group group, String subGroupName);

    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName);

    public List<Group> getActiveTopLevelGroups(User user);

    public List<Group> getSubGroups(Group group);

    public boolean hasSubGroups(Group group);

    public List<User> getUsersInGroupNotSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Long groupId);

    public List<User> getAllUsersInGroupAndSubGroups(Group group);

    boolean hasParent(Group group);

    public Group getParent(Group group);

    public Group linkSubGroup(Group child, Group parent);

    List<Group> getAllParentGroups(Group group);

    /*
    Pass in the group you want to make a child as the 'possibleChildGroup', and the desired parent
    as the 'possibleParentGroup', and this will return true if the possible child is already in the parent chain
     of the possible parent, i.e., if it will create an infinite loop
     */
    boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup);

    /*
    Methods to set and retrieve some basic group properties
     */

    public Group setGroupDefaultReminderMinutes(Group group, Integer minutes);

    public Group setGroupDefaultReminderMinutes(Long groupId, Integer minutes);

    public Group setGroupDefaultLanguage(Group group, String locale);

    public Group setGroupAndSubGroupDefaultLanguage(Group group, String locale);

    public boolean hasDefaultLanguage(Group group);

    public void assignDefaultLanguage(Group group, User user);

    public Integer getGroupSize(Group group, boolean includeSubGroups);

    public Integer getGroupSize(Long groupId, boolean includeSubGroups);

    /*
    Three methods to get when a group last 'did' something, or else was modified
     */
    public LocalDateTime getLastTimeGroupActive(Group group);

    public LocalDateTime getLastTimeGroupModified(Group group);

    public LocalDateTime getLastTimeSubGroupActive(Group group);

    /*
    Methods to consolidate groups, and to manage active / inactive
     */

    public Group setGroupInactive(Group group);

    public Group setGroupInactive(Long groupId);

    public Group mergeGroups(Long firstGroupId, Long secondGroupId);

    public Group mergeGroupsLeaveActive(Long firstGroupId, Long secondGroupId);

    public Group mergeGroupsIntoNew(Long firstGroupId, Long secondGroupId, String newGroupName, User creatingUser);

    public Group mergeGroups(Group firstGroup, Group secondGroup);

    public Group mergeGroups(Group firstGroup, Group secondGroup, boolean setConsolidatedGroupInactive);

    public Group mergeGroupsSpecifyOrder(Group groupInto, Group groupFrom, boolean setFromGroupInactive);

    public Group mergeGroupsSpecifyOrder(Long groupIntoId, Long groupFromId, boolean setFromGroupInactive);

    public List<Group> getMergeCandidates(User mergingUser, Long firstGroupSelected);

    public Long[] orderPairByNumberMembers(Long groupId1, Long groupId2);

    /*
    Methods to get group properties if paid or not
     */

    public boolean isGroupPaid(Group group);

    public boolean canGroupDoFreeForm(Group group);

    public boolean canGroupRelayMessage(Group group);

    /*
    Methods for system and account admin
     */

    List<Group> getAllGroups();

    Page<Group> getAllActiveGroupsPaginated(Integer pageNumber, Integer pageSize);

    List<Group> getGroupsFiltered(User createdByUser, Integer minGroupSize, Date createdAfterDate, Date createdBeforeDate);

    List<GroupTreeDTO> getGroupsMemberOfTree(Long userId);

    List<LocalDate> getMonthsGroupActive(Group group);

    /*
    Recursive query better to use than recursive code calls
    */
    List<Group> findGroupAndSubGroupsById(Long groupId);
}
