package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupTreeDTO;

import java.sql.Timestamp;
import java.util.Date;
import java.util.List;

/**
 * @author Lesetse Kimwaga
 */
public interface GroupManagementService {

    /*
    Methods to create groups
     */

    public Group createNewGroup(User creatingUser, String groupName);

    public Group createNewGroup(User creatingUser, List<String> phoneNumbers);

    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers);

    /*
    Methods to load, find, save groups
    */

    public Group loadGroup(Long groupId);

    public List<Group> getGroupsFromUser(User sessionUser);

    public List<Group> getCreatedGroups(User creatingUser);

    public List<Group> getGroupsPartOf(User sessionUser);

    public List<Group> getActiveGroupsPartOf(User sessionUser);

    // public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize);

    // public Page<Group> getPageOfGroups(User sessionUser, int pageNumber, int pageSize);

    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize);

    public Group getGroupById(Long groupId);

    public Group saveGroup(Group groupToSave);

    public void deleteGroup(Group groupToDelete);

    public boolean canUserMakeGroupInactive(User user, Group group);

    public boolean canUserMakeGroupInactive(User user, Long groupId);

    public boolean isGroupCreatedByUser(Long groupId, User user);

    /*
    Methods to find, and add group members
     */

    public boolean isUserInGroup(Group group, User user);

    public Group addGroupMember(Group currentGroup, User newMember);

    public Group addGroupMember(Long currentGroupId, Long newMemberId);

    public Group removeGroupMember(Group group, User user);

    public Group removeGroupMember(Long groupId, User user);

    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList);

    public Group addNumberToGroup(Long groupId, String phoneNumber);

    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers);

    /*
    Methods to find if a user has an outstanding group management action to perform or groups on which they can perform it
     */

    public Group getLastCreatedGroup(User creatingUser);

    public boolean needsToRenameGroup(User sessionUser);

    public Long groupToRename(User sessionUser);

    public Group renameGroup(Group group, String newGroupName);

    public List<Group> groupsOnWhichCanCallVote(User user);

    public boolean canUserCallVoteOnAnyGroup(User user);

    /*
    Methods to work with group joining tokens
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

    /*
    Methods do deal with sub groups and parent groups
     */

    public Group createSubGroup(User createdByUser, Group group, String subGroupName);

    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName);

    public List<Group> getActiveTopLevelGroups(User user);

    public List<Group> getSubGroups(Group group);

    public boolean hasSubGroups(Group group);

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

    /*
    Methods to consolidate groups, and to manage active / inactive
     */

    public Group setGroupInactive(Group group);

    public Group setGroupInactive(Long groupId);

    public Group mergeGroups(Long firstGroupId, Long secondGroupId);

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

    Page<Group> getAllGroupsPaginated(Integer pageNumber, Integer pageSize);

    List<Group> getGroupsFiltered(User createdByUser, Integer minGroupSize, Date createdAfterDate, Date createdBeforeDate);

    List<GroupTreeDTO> getGroupsMemberOfTree(Long userId);
}
