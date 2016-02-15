package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.dto.NewGroupMember;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.util.TokenGeneratorService;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;

/**
 * @author luke on 2015/08/14.
 *
 */

@Service
@Transactional
public class GroupManager implements GroupManagementService {

    private final static Logger log = LoggerFactory.getLogger(GroupManager.class);

    /*
    N.B.
    When we refactor to pass the user doing actions around so that it can be recorded then replace the
    dontKnowTheUser whereever it is used with the actual user
     */

    private final Long dontKnowTheUser = 0L;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
    private UserManagementService userManager;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    private GroupLogRepository groupLogRepository;

//    @Autowired
//    private EventRepository eventRepository;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private PermissionsManagementService permissionsManager;

    @Autowired
    private GroupAccessControlManagementService accessControlService;


    /*
    First, methods to create groups
     */

    @Override
    public Group createNewGroup(User creatingUser, String groupName, boolean addDefaultRole) {
        Long timeStart = System.currentTimeMillis();
        Group group = groupRepository.save(new Group(groupName, creatingUser));
        recordGroupLog(group.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED, 0L, "");;
        if (addDefaultRole) roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, creatingUser, creatingUser);
        Long timeEnd = System.currentTimeMillis();
        log.info(String.format("Creating a group without roles, time taken ... %d msecs", timeEnd - timeStart));
        return group;
    }

    @Override
    public Group createNewGroupWithCreatorAsMember(User creatingUser, String groupName, boolean addDefaultRole) {
        Group group = new Group(groupName, creatingUser);
        group.addMember(creatingUser);
        Group savedGroup = groupRepository.save(group);
        recordGroupLog(savedGroup.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED,0L, "");
        if (addDefaultRole) roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, creatingUser, creatingUser);
        return savedGroup;
    }

    @Override
    public Group createNewGroup(User creatingUser, List<String> phoneNumbers, boolean addDefaultRoles) {
        // todo: check if a similar group exists and if so prompt
        Group groupToCreate = createNewGroupWithCreatorAsMember(creatingUser, "", addDefaultRoles);
        return addNumbersToGroup(groupToCreate.getId(), phoneNumbers, creatingUser, addDefaultRoles);
    }

    @Override
    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers, boolean addDefaultRoles) {
        return createNewGroup(userManager.getUserById(creatingUserId), phoneNumbers, addDefaultRoles);
    }

    @Override
    public Group saveGroup(Group groupToSave, boolean createGroupLog, String description, Long changedByuserId) {
        Group group = groupRepository.save(groupToSave);
        if (createGroupLog) recordGroupLog(groupToSave.getId(),changedByuserId, GroupLogType.GROUP_UPDATED,0L,description);
        return group;
    }

    @Override
    public Group renameGroup(Group group, String newGroupName) {
        // only bother if the name has changed (in some instances, web app may call this without actual name change)
        if (!group.getGroupName().equals(newGroupName)) {
            String oldName = group.getGroupName();
            group.setGroupName(newGroupName);
            return saveGroup(group,true,String.format("Old name: %s, New name: %s",oldName,newGroupName),dontKnowTheUser);
        } else {
            return group;
        }
    }

    @Override
    public Group renameGroup(Long groupId, String newGroupName) {
        return renameGroup(loadGroup(groupId), newGroupName);
    }

    // @Async
    @Override
    public void recordGroupLog(Long groupId, Long userDoingId, GroupLogType type, Long userOrGroupAffectedId, String description) {
        groupLogRepository.save(new GroupLog(groupId, userDoingId, type, userOrGroupAffectedId, description));
    }

    /**
     * SECTION: Methods to add and remove group members, including logging & roles/permissions
     */

    // @Async
    public void wireNewGroupMemberLogsRoles(Group group, User newMember, Long addingUserId, boolean addDefaultRole) {

        if (hasDefaultLanguage(group) && !newMember.isHasInitiatedSession())
            assignDefaultLanguage(group, newMember);

        Long savingUserId = (addingUserId == null) ? dontKnowTheUser : addingUserId;

        groupLogRepository.save(new GroupLog(group.getId(), savingUserId, GroupLogType.GROUP_MEMBER_ADDED, newMember.getId()));

        if (addDefaultRole) {
            if (SecurityContextHolder.getContext().getAuthentication() == null) {
                roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_ORDINARY_MEMBER, group,
                                                                   newMember, userManager.getUserById(addingUserId));
            } else {
                roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_ORDINARY_MEMBER, group, newMember);

            }
        }

        jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(group,newMember));
    }

    // @Async
    public void removeGroupMemberLogsRoles(Group group, User oldMember, User removingUser) {
        Long removingUserId = (removingUser == null) ? dontKnowTheUser : removingUser.getId();
        String description = (oldMember.getId() == removingUserId) ? "Unsubscribed" : "Removed from group";
        groupLogRepository.save(new GroupLog(group.getId(), removingUserId, GroupLogType.GROUP_MEMBER_REMOVED,
                                             oldMember.getId(), description));
        roleManagementService.removeGroupRolesFromUser(oldMember, group);
    }

    @Override
    public Group addGroupMember(Group currentGroup, User newMember, Long addingUserId, boolean addDefaultRole) {
        // todo: make sure transaction management is working alright
        if (currentGroup.getGroupMembers().contains(newMember)) {
            return currentGroup;
        } else {
            currentGroup.addMember(newMember);
            currentGroup = saveGroup(currentGroup,false,"",dontKnowTheUser);
            newMember = userManager.save(newMember); // so that this is isntantly double-sided, else getting access control errors
            wireNewGroupMemberLogsRoles(currentGroup, newMember, addingUserId, addDefaultRole);
            return currentGroup;
        }
    }

    @Override
    public Group addGroupMember(Long currentGroupId, Long newMemberId, Long addingUserId, boolean addDefaultRole) {
        return addGroupMember(loadGroup(currentGroupId), userManager.getUserById(newMemberId), addingUserId, addDefaultRole);
    }

    @Override
    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers, User addingUser, boolean addDefaultRoles) {

        Group groupToExpand = loadGroup(groupId);

        List<User> groupNewMembers = userManager.getUsersFromNumbers(phoneNumbers);
        for (User newMember : groupNewMembers)
            groupToExpand.addMember(newMember);
        Group savedGroup = groupRepository.save(groupToExpand);

        for (User newMember : groupNewMembers) // do this after else risk async getting in way before joins established
            wireNewGroupMemberLogsRoles(savedGroup, newMember, addingUser.getId(), addDefaultRoles);

        return savedGroup;
    }
    @Override
    public Group addMembersToGroup(Long groupId, List<User> members, boolean isClosedGroup){

        Group groupToExpand = loadGroup(groupId);
        groupToExpand.getGroupMembers().addAll(members);
        Group savedGroup = groupRepository.save(groupToExpand);
        for (User newMember : members) {
            GroupLog groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),dontKnowTheUser,GroupLogType.GROUP_MEMBER_ADDED,newMember.getId()));
            jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(groupToExpand,newMember));
        }
        return savedGroup;

    }

    @Override
    public Group removeGroupMember(Group group, User user, User removingUser) {
        // todo: error handling
        group.getGroupMembers().remove(user);
        Group savedGroup = saveGroup(group,false,"",dontKnowTheUser);
        removeGroupMemberLogsRoles(savedGroup, user, removingUser);
        return savedGroup;
    }

    @Override
    public Group removeGroupMember(Long groupId, User user, User removingUser) {
        return removeGroupMember(loadGroup(groupId), user, removingUser);
    }

    @Override
    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList, Long modifyingUserId, boolean addDefaultRoles) {

        List<User> originalUsers = new ArrayList<>(group.getGroupMembers());

        // todo: we need to log each of these removals, hence doing it this way, but should refactor
        for (User user : originalUsers) {
            if (!revisedUserList.contains(user)) {
                log.info("Removing a member: " + user);
                removeGroupMember(group, user, null);
            }
        }

        for (User user : revisedUserList) {
            if (!originalUsers.contains(user)) {
                log.info("Adding a member: " + user);
                addGroupMember(group, user, modifyingUserId, addDefaultRoles);
            }
        }

        return saveGroup(group,false,"",dontKnowTheUser);
    }

    /*
    SECTION: Loading groups, finding properties, etc
     */


    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public String getGroupName(Long groupId) {
        return loadGroup(groupId).getName("");
    }

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUserAndActive(creatingUser, true);
    }

    @Override
    public List<Group> getActiveGroupsPartOf(User sessionUser) {
        return groupRepository.findByGroupMembersAndActive(sessionUser, true);
    }
    @Override
    public List<Group> getActiveGroupsPartOfOrdered(User sessionUser){
        return groupRepository.findActiveUserGroupsOrderedByRecentActivity(sessionUser.getId());
    }

    @Override
    public List<Group> getActiveGroupsPartOf(Long userId) {
        return getActiveGroupsPartOf(userManager.getUserById(userId));
    }

    @Override
    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize) {
        return groupRepository.findByGroupMembersAndActive(sessionUser, new PageRequest(pageNumber, pageSize), true);
    }

    @Override
    public List<Group> getListGroupsFromLogbooks(List<LogBook> logBooks) {
        // seems like doing it this way more efficient than running lots of group fetch queries, but need to test/verify
        log.info("Got a list of logbooks ... look like this: " + logBooks);
        List<Long> ids = new ArrayList<>();
        for (LogBook entry : logBooks) { ids.add(entry.getGroupId()); }
        log.info("And now we have this list of Ids ... " + ids);
        return groupRepository.findAllByIdIn(ids);
    }

    /*
    Methods to find if a user has an outstanding group management action to perform or groups on which they can perform it
     */

    @Override
    public boolean isUserInGroup(Group group, User user) {
        // at some point may want to make this more efficient than getter method
        return group.getGroupMembers().contains(user);
    }

    /*
    Methods to implement finding last group and prompting to rename if unnamed. May make this a little more complex
    in future (e.g., check not just last created group, but any unnamed created groups above X users, or so on). Hence
    a little duplication / redundancy for now.
     */
    @Override
    public Group getLastCreatedGroup(User creatingUser) {
        return groupRepository.findFirstByCreatedByUserOrderByIdDesc(creatingUser);
    }

    @Override
    public boolean needsToRenameGroup(User sessionUser) {
        // todo: reach back further than just the last created group ... any group that's unnamed
        Group lastCreatedGroup = getLastCreatedGroup(sessionUser);
        return (lastCreatedGroup != null && lastCreatedGroup.isActive() && !lastCreatedGroup.hasName());
    }

    @Override
    public Group groupToRename(User sessionUser) {
        return getLastCreatedGroup(sessionUser);
    }

    @Override
    public boolean hasActiveGroupsPartOf(User user) {
        return !getActiveGroupsPartOf(user).isEmpty();
        // return groupRepository.countActiveGroups(user.getId()) > 0; // this is breaking the getActiveGroups methods
    }

    @Override
    public boolean canUserMakeGroupInactive(User user, Group group) {
        // todo: Integrate with permission checking -- for now, just checking if group created by user in last 48 hours
        // todo: the time checking would be so much easier if we use Joda or Java 8 DateTime ...
        boolean createdByUser = (group.getCreatedByUser().getId() == user.getId()); // full equals doesn't work on session-loaded user
        Timestamp thresholdTime = new Timestamp(Calendar.getInstance().getTimeInMillis() - (48 * 60 * 60 * 1000));
        boolean groupCreatedSinceThreshold = (group.getCreatedDateTime().after(thresholdTime));
        return (createdByUser && groupCreatedSinceThreshold);
    }

    @Override
    public boolean canUserMakeGroupInactive(User user, Long groupId) {
        return canUserMakeGroupInactive(user, loadGroup(groupId));
    }

    @Override
    public boolean isGroupCreatedByUser(Long groupId, User user) {
        return (loadGroup(groupId).getCreatedByUser() == user);
    }

    @Override
    public boolean canUserModifyGroup(Group group, User user) {
        Permission permission = permissionsManager.findByName(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        return accessControlService.hasGroupPermission(permission, group, user);
    }

    /*
    Methods to work with group joining tokens and group discovery
     */

    @Override
    public Group getGroupByToken(String groupToken) {
        Group groupToReturn = groupRepository.findByGroupTokenCode(groupToken);
        if (groupToReturn == null) return null;
        if (groupToReturn.getTokenExpiryDateTime().before(Timestamp.valueOf(LocalDateTime.now()))) return null;
        return groupToReturn;
    }

    @Override
    public Group generateGroupToken(Long groupId, User generatingUser) {
        // todo: check permissions
        return generateGroupToken(loadGroup(groupId), generatingUser);
    }

    @Override
    public Group generateGroupToken(Group group, User generatingUser) {
        log.info("Generating a token code that is indefinitely open, within postgresql range ... We will have to adjust this before the next century");
        final Timestamp endOfCentury = Timestamp.valueOf(LocalDateTime.of(2099, 12, 31, 23, 59));
        group.setGroupTokenCode(generateCodeString());
        group.setTokenExpiryDateTime(endOfCentury);
        return saveGroup(group, true, String.format("Set Group Token: %s",group.getGroupTokenCode()), generatingUser.getId());
    }

    @Override
    public Group generateGroupToken(Group group, Integer daysValid, User user) {
        // todo: checks for whether the code already exists, and/or existing validity of group

        log.info("Generating a new group token, for group: " + group.getId());
        Group groupToReturn;
        if (daysValid == 0) {
            groupToReturn = generateGroupToken(group, user);
        } else {

            Integer daysMillis = 24 * 60 * 60 * 1000;
            Timestamp expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + daysValid * daysMillis);

            group.setGroupTokenCode(generateCodeString());
            group.setTokenExpiryDateTime(expiryDateTime);

            log.info("Group code generated: " + group.getGroupTokenCode());

            groupToReturn = saveGroup(group,true,String.format("Set Group Token: %s",group.getGroupTokenCode()),user.getId());

            log.info("Group code after save: " + group.getGroupTokenCode());
        }

        return groupToReturn;
    }

    @Override
    public Group generateGroupToken(Long groupId, Integer daysValid, User user) {
        return generateGroupToken(loadGroup(groupId), daysValid, user);
    }

    @Override
    public Group extendGroupToken(Group group, Integer daysExtension, User user) {
        Integer daysMillis = 24 * 60 * 60 * 1000; // need to put this somewhere else so not copying & pasting
        Timestamp newExpiryDateTime = new Timestamp(group.getTokenExpiryDateTime().getTime() + daysExtension * daysMillis);
        group.setTokenExpiryDateTime(newExpiryDateTime);
        return saveGroup(group,true,String.format("Extend group token %s to %s",group.getGroupTokenCode(),newExpiryDateTime.toString()),user.getId());
    }

    @Override
    public Group invalidateGroupToken(Group group, User user) {
        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        group.setGroupTokenCode(null); // alternately, set it to ""
        return saveGroup(group,true,"Invalidate Group Token",user.getId());
    }

    @Override
    public Group invalidateGroupToken(Long groupId, User user) {
        return invalidateGroupToken(loadGroup(groupId), user);
    }

    @Override
    public boolean groupHasValidToken(Group group) {

        boolean codeExists = group.getGroupTokenCode() != null && group.getGroupTokenCode().trim() != "";
        boolean codeValid = group.getTokenExpiryDateTime() != null &&
                group.getTokenExpiryDateTime().after(new Timestamp(Calendar.getInstance().getTimeInMillis()));

        return codeExists && codeValid;

    }

    @Override
    public boolean tokenExists(String groupToken) {
        // separating this from getGroupByToken because in time we will want to hone its performance, a lot
        // todo: find a way to make this very, very fast--in some use cases, will be triggered by 10k+ users within seconds
        log.info("Looking for this token ... " + groupToken);
        return (groupRepository.findByGroupTokenCode(groupToken) != null);
    }

    @Override
    public Group setGroupDiscoverable(Group group, boolean discoverable, Long userId) {
        // todo: create a dedicated permission for this, and uncomment, when we have permission setting working on group create
        // todo: once we have implemented 'request to join', will need to wire that up here
        if (group.isDiscoverable() == discoverable) return group;
        String logEntry = discoverable ? "Set group publicly discoverable" : "Set group hidden from public";
        group.setDiscoverable(discoverable);
        return saveGroup(group, true, logEntry, userId);
    }

    @Override
    public List<Group> findDiscoverableGroups(String groupName) {
        return groupRepository.findByGroupNameContainingAndDiscoverable(groupName, true);
    }

    private String generateCodeString() {
        return String.valueOf(tokenGeneratorService.getNextToken());
    }

    /**
     * Methods for working with subgroups
     */

    /*
    returns the new sub-group
     */
    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName) {
        return createSubGroup(userManager.getUserById(createdByUserId), loadGroup(groupId), subGroupName);
    }

    public Group createSubGroup(User createdByUser, Group group, String subGroupName) {
        Group subGroup = groupRepository.save(new Group(subGroupName, createdByUser, group));
        recordGroupLog(group.getId(),createdByUser.getId(),GroupLogType.SUBGROUP_ADDED, subGroup.getId(),
                       String.format("Subgroup: %s added",subGroupName));
        return subGroup;
    }

    @Override
    public List<Group> getSubGroups(Group group) {
        return groupRepository.findByParent(group);
    }

    @Override
    public boolean hasSubGroups(Group group) {
        // slightly redundant for now, but if trees get large & complex may want to replace with quick count query (a la 'if user exists')
        return !getSubGroups(group).isEmpty();
    }

    @Override
    public List<User> getUsersInGroupNotSubGroups(Long groupId) {
        return loadGroup(groupId).getGroupMembers();
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Long groupId) {
        return getAllUsersInGroupAndSubGroups(loadGroup(groupId));
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Group group) {
        List<User> userList = new ArrayList<User>();
        recursiveUserAdd(group, userList);
        return userList;
    }

    @Override
    public List<Group> getAllParentGroups(Group group) {
        List<Group> parentGroups = new ArrayList<Group>();
        recursiveParentGroups(group, parentGroups);
        return parentGroups;
    }

    @Override
    public boolean hasParent(Group group) {
        return group.getParent() != null;
    }

    @Override
    public Group getParent(Group group) {
        // trivial method, but seems safer to do a trivial services call than have this sort of thing sitting in view
        return group.getParent();
    }

    @Override
    public Group linkSubGroup(Group child, Group parent) {
        // todo: error checking, for one more barrier against infintite loops
        child.setParent(parent);
        Group savedChild = groupRepository.save(child);
        /*
        Bit of reversed logic therefore not putting it in saveGroup
         */
        String description = String.format("Linked group: %s to %s",child.getGroupName().trim().equals("") ? child.getId() : child.getGroupName(),
                parent.getGroupName().trim().equals("") ? parent.getId() : parent.getGroupName());
        GroupLog groupLog = groupLogRepository.save(new GroupLog(parent.getId(),dontKnowTheUser,GroupLogType.SUBGROUP_ADDED,child.getId(),description));
        return savedChild;
    }

    @Override
    public boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup) {
        for (Group g : getAllParentGroups(possibleParentGroup)) {
            // if this returns true, then the group being passed as child is already in the parent chain of the desired
            // parent, which will create an infinite loop, hence prevent it
            if (g.getId() == possibleChildGroup.getId()) return true;
        }
        return false;
    }

    /**
     * Section of methods to add and remove a range of group properties
     */

    @Override
    public Group setGroupDefaultReminderMinutes(Group group, Integer minutes) {
        group.setReminderMinutes(minutes);
        return saveGroup(group,true,String.format("Set reminder minutes to %d",minutes),dontKnowTheUser);
    }

    @Override
    public Group setGroupDefaultLanguage(Group group, String locale) {

        /*
         todo: the copying of the list is probably an expensive way to do this, but better ways to avoid concurrent modification error are above my pay grade.
          */

        log.info("Okay, we are inside the group language setting function ...");
        List<User> userList = new ArrayList<>(group.getGroupMembers());

        for (User user : userList) {
            if (!user.isHasInitiatedSession()) {
                log.info("User hasn't set their own language, so adjusting it to: " + locale + " for this user: " + user.nameToDisplay());
                user.setLanguageCode(locale);
                userManager.save(user);
            }
        }
        group.setDefaultLanguage(locale);
        return saveGroup(group,true,String.format("Set default language to %s", locale),dontKnowTheUser);

    }

    @Override
    public Group setGroupAndSubGroupDefaultLanguage(Group group, String locale) {

        group = setGroupDefaultLanguage(group, locale);

        // todo: there is almost certainly a more elegant way to do this recursion
        if (hasSubGroups(group)) {
            for (Group subGroup : getSubGroups(group))
                setGroupAndSubGroupDefaultLanguage(subGroup, locale);
        }

        return group;
    }

    @Override
    public boolean hasDefaultLanguage(Group group) {
        return (group.getDefaultLanguage() != null && !group.getDefaultLanguage().trim().equals("en"));
    }

    @Override
    public void assignDefaultLanguage(Group group, User user) {
        user.setLanguageCode(group.getDefaultLanguage());
        userManager.save(user);
    }

    @Override
    public Integer getGroupSize(Group group, boolean includeSubGroups) {
        // as with a few above, a little trivial as implemented here, but may change in future, so rather here than code in webapp

        log.info("Getting group member size");
        if (!includeSubGroups) {
            log.info("Getting group size, for group: " + group);
            return group.getGroupMembers().size();
        } else {
            log.info("Getting group size, including sub-groups, for group:" + group);
            return getAllUsersInGroupAndSubGroups(group).size();
        }

    }

    @Override
    public Integer getGroupSize(Long groupId, boolean includeSubGroups) {
        return getGroupSize(loadGroup(groupId), includeSubGroups);
    }

    @Override
    public LocalDateTime getLastTimeGroupActive(Group group) {
        // todo: we should upgrade this to a slightly complex single query that checks both events and grouplogs at once
        // for present, if it finds an event, returns the event, else returns the date of last modification, likely date of creation
        // Event lastEvent = eventRepository.findFirstByAppliesToGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        // if (lastEvent == null)
            return getLastTimeGroupModified(group);
        //else
        //    return lastEvent.getEventStartDateTime().toLocalDateTime();
    }

    @Override
    public LocalDateTime getLastTimeGroupModified(Group group) {
        // todo: change groupLog to use localdatetime
        GroupLog latestGroupLog = groupLogRepository.findFirstByGroupIdOrderByCreatedDateTimeDesc(group.getId());
        return (latestGroupLog != null) ? LocalDateTime.ofInstant(latestGroupLog.getCreatedDateTime().toInstant(), ZoneId.systemDefault()) :
                group.getCreatedDateTime().toLocalDateTime();
    }

    @Override
    public LocalDateTime getLastTimeSubGroupActive(Group group) {
        // todo: try make this work with recursive call instead of highly inefficient code loop
        LocalDateTime lastTimeActive = getLastTimeGroupActive(group);
        if (hasSubGroups(group)) {
            LocalDateTime currentChildLastActive;
            for (Group child : getSubGroups(group)) {
                currentChildLastActive = getLastTimeGroupActive(child);
                lastTimeActive = (lastTimeActive.isAfter(currentChildLastActive)) ? lastTimeActive : currentChildLastActive;
            }
        }
        return lastTimeActive;
    }

    @Override
    public Group setGroupInactive(Group group, User user) {
        // todo errors and exception throwing
        group.setActive(false);
        Group savedGroup = groupRepository.save(group);
        recordGroupLog(group.getId(),user.getId(),GroupLogType.GROUP_REMOVED,0L,String.format("Set group inactive"));
        return savedGroup;
    }

    @Override
    public Group setGroupInactive(Long groupId, User user) {
        return setGroupInactive(loadGroup(groupId), user);
    }

    @Override
    public Group mergeGroups(Long firstGroupId, Long secondGroupId, Long mergingUserId) {
        log.info("Okay, trying to merge these groups");
        return mergeGroups(loadGroup(firstGroupId), loadGroup(secondGroupId), mergingUserId);
    }

    @Override
    public Group mergeGroupsLeaveActive(Long firstGroupId, Long secondGroupId, Long mergingUserId) {
        return mergeGroups(loadGroup(firstGroupId), loadGroup(secondGroupId), false, mergingUserId);
    }

    @Override
    public Group mergeGroupsIntoNew(Long firstGroupId, Long secondGroupId, String newGroupName, User creatingUser) {
        Group consolidatedGroup = new Group(newGroupName, creatingUser);
        Set<User> setOfMembers = new HashSet<>(loadGroup(firstGroupId).getGroupMembers());
        setOfMembers.addAll(loadGroup(secondGroupId).getGroupMembers());

        consolidatedGroup.setGroupMembers(new ArrayList<>(setOfMembers));
        Group savedGroup = saveGroup(consolidatedGroup,true,String.format("Merged group %d with %d",secondGroupId,firstGroupId),creatingUser.getId());
        for (User u : setOfMembers)
            wireNewGroupMemberLogsRoles(savedGroup, u, creatingUser.getId(), true);

        return savedGroup;
    }

    @Override
    public Group mergeGroups(Group groupA, Group groupB, Long mergingUserId) {
        return mergeGroups(groupA, groupB, true, mergingUserId);
    }

    @Override
    public Group mergeGroups(Group groupA, Group groupB, boolean setConsolidatedGroupInactive, Long mergingUserId) {

        Group largerGroup, smallerGroup;

        // note: if the groups are the same size, this will default to merging into the first-passed group
        if (groupA.getGroupMembers().size() >= groupB.getGroupMembers().size()) {
            largerGroup = groupA;
            smallerGroup = groupB;
        } else {
            largerGroup = groupB;
            smallerGroup = groupA;
        }

        return mergeGroupsSpecifyOrder(largerGroup, smallerGroup, setConsolidatedGroupInactive, mergingUserId);
    }

    @Override
    public Group mergeGroupsSpecifyOrder(Group groupInto, Group groupFrom, boolean setFromGroupInactive, Long mergingUserId) {

        // todo: optimize this, almost certainly very slow
        // todo: figure out how to transfer roles ... original group roles move over?
        for (User user : new ArrayList<>(groupFrom.getGroupMembers()))
            addGroupMember(groupInto, user, mergingUserId, false);

        groupFrom.setActive(!setFromGroupInactive);
        saveGroup(groupFrom,true,String.format("Set group %d inactive",groupFrom.getId()),dontKnowTheUser);
        return saveGroup(groupInto,true,String.format("Merged group %d into %d",groupFrom.getId(),groupInto.getId()),dontKnowTheUser);
    }

    @Override
    public Group mergeGroupsSpecifyOrder(Long groupIntoId, Long groupFromId, boolean setFromGroupInactive, Long mergingUserId) {
        return mergeGroupsSpecifyOrder(loadGroup(groupIntoId), loadGroup(groupFromId), setFromGroupInactive, mergingUserId);
    }

    @Override
    public List<Group> getMergeCandidates(User mergingUser, Long firstGroupSelected) {

        // todo: lots of error handling etc
        List<Group> createdGroups = getCreatedGroups(mergingUser);
        createdGroups.remove(loadGroup(firstGroupSelected));
        return createdGroups;
    }

    @Override
    public Long[] orderPairByNumberMembers(Long groupId1, Long groupId2) {
        Integer group1size = loadGroup(groupId1).getGroupMembers().size(),
                group2size = loadGroup(groupId2).getGroupMembers().size();
        return (group1size >= group2size) ? new Long[] {groupId1, groupId2} : new Long[] {groupId2, groupId1};
    }

    @Override
    public boolean isGroupPaid(Group group) {
        return false;
    }

    @Override
    public boolean canGroupDoFreeForm(Group group) {
        return false;
    }

    @Override
    public boolean canGroupRelayMessage(Group group) {
        return false;
    }

    @Override
    public List<Group> getAllGroups() {
        return groupRepository.findAll();
    }

    @Override
    public Page<Group> getAllActiveGroupsPaginated(Integer pageNumber, Integer pageSize) {
        return groupRepository.findAllByActiveOrderByIdAsc(true, new PageRequest(pageNumber, pageSize));
    }

    @Override
    public List<Group> getGroupsFiltered(User createdByUser, Integer minGroupSize, Date createdAfterDate, Date createdBeforeDate) {
        /*
        Note: this is an extremely expensive way to do what follows, and needs to be fixed in due course, but for now it'll be called
         rarely, and just by system admin, on at most a few hundred groups.
          */

        List<Group> allGroups = getAllGroups();
        List<Group> filteredGroups = new ArrayList<>(allGroups);

        if (createdByUser != null) {
            for (Group group : allGroups)
                if (group.getCreatedByUser() != createdByUser)
                    filteredGroups.remove(group);
        }

        if (minGroupSize != null) {
            for (Group group : allGroups)
                if (group.getGroupMembers().size() < minGroupSize)
                    filteredGroups.remove(group);
        }

        if (createdAfterDate != null) {
            for (Group group : allGroups)
                if (group.getCreatedDateTime().before(new Timestamp(createdAfterDate.getTime())))
                    filteredGroups.remove(group);
        }

        if (createdBeforeDate != null) {
            for (Group group : allGroups)
                if (group.getCreatedDateTime().after(new Timestamp(createdBeforeDate.getTime())));
        }

        return filteredGroups;
    }

    @Override
    public List<GroupTreeDTO> getGroupsMemberOfTree(Long userId) {
        List<Object[]> listObjArray = groupRepository.getGroupMemberTree(userId);
        List<GroupTreeDTO> list = new ArrayList<>();
        for (Object[] objArray : listObjArray) {
            list.add(new GroupTreeDTO(objArray));
        }
        return list;
    }

    @Override
    public List<LocalDate> getMonthsGroupActive(Group group) {
        // todo: make this somewhat more sophisticated, including checking for active/inactive months, paid months etc
        LocalDate groupStartDate = group.getCreatedDateTime().toLocalDateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate monthIterator = LocalDate.of(groupStartDate.getYear(), groupStartDate.getMonth(), 1);
        List<LocalDate> months = new ArrayList<>();

        while (monthIterator.isBefore(today)) {
            months.add(monthIterator);
            monthIterator = monthIterator.plusMonths(1L);
        }

        return months;
    }

    /*
    Recursive query better to use than recursive code calls
     */
    @Override
    public List<Group> findGroupAndSubGroupsById(Long groupId) {
        return groupRepository.findGroupAndSubGroupsById(groupId);
    }

    private void recursiveUserAdd(Group parentGroup, List<User> userList ) {

        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
            recursiveUserAdd(childGroup,userList);
        }

        // add all the users at this level
        userList.addAll(parentGroup.getGroupMembers());

    }

    private void recursiveParentGroups(Group childGroup, List<Group> parentGroups) {
        if (childGroup.getParent() != null && childGroup.getParent().getId() != 0) {
            recursiveParentGroups(childGroup.getParent(),parentGroups);
        }
        // add the current group as there are no more parents
        // todo aakil this adds the group even if it had no parents??? is this a problem, rethink
        parentGroups.add(childGroup);
    }



}
