package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.dto.NewGroupMember;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.EventRepository;
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

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private PermissionsManagementService permissionsManager;

    @Autowired
    private GroupAccessControlManagementService accessControlService;


    /**
     * Have not yet created methods analogous to those in UserManager, as not sure if necessary
     * For the moment, using this to expose some basic group services for the application interfaces
     */

    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public Group secureLoadGroup(Long id) {
        return loadGroup(id);
    }

    @Override
    public List<Group> getGroupsFromUser(User sessionUser) {
        // todo: add pagination
        return sessionUser.getGroupsPartOf();
    }

    @Override
    public boolean isUserInGroup(Group group, User user) {
        // at some point may want to make this more efficient than getter method
        return group.getGroupMembers().contains(user);
    }

    @Override
    public Group saveGroup(Group groupToSave, boolean createGroupLog, String description, Long changedByuserId) {
        Group group = groupRepository.save(groupToSave);
        if (createGroupLog) {
            GroupLog groupLog = groupLogRepository.save(new GroupLog(groupToSave.getId(),changedByuserId, GroupLogType.GROUP_UPDATED,0L,description));
        }
        return group;
    }

    /* @Override
    public void deleteGroup(Group groupToDelete) {

        //there are issues with cascading if we delete the group before removing all users, hence doing it this way
        //which will be quite slow, but this function should almost never be used, so not a major issue, for now, and
        //rather safe than sorry on deletion.

        List<User> members = new ArrayList<>(groupToDelete.getGroupMembers());
        log.info("We are now going to delete a group ... first, we unsubscribe " + members.size() + " members");

        for (User user : members) {
            groupToDelete = removeGroupMember(groupToDelete, user);
        }

        log.info("Group members removed ... " + groupToDelete.getGroupMembers().size() + " members left. Proceeding to delete");

        groupRepository.delete(groupToDelete);
    }*/


    @Override
    public Group addGroupMember(Long currentGroupId, Long newMemberId) {
        return addGroupMember(loadGroup(currentGroupId), userManager.getUserById(newMemberId));
    }

    @Override
    public Group removeGroupMember(Group group, User user) {
        // todo: error handling
        group.getGroupMembers().remove(user);
        Group savedGroup = saveGroup(group,false,"",dontKnowTheUser);
        GroupLog groupLog = groupLogRepository.save(new GroupLog(group.getId(),dontKnowTheUser,GroupLogType.GROUP_MEMBER_REMOVED,user.getId()));
        return savedGroup;
    }

    @Override
    public Group removeGroupMember(Long groupId, User user) {
        return removeGroupMember(loadGroup(groupId), user);
    }

    @Override
    public Group unsubscribeMember(Group group, User user) {
        // with logging, this behaves different from standard remove member, though may consolidate in future
        group.getGroupMembers().remove(user);
        Group savedGroup = saveGroup(group, false, "", dontKnowTheUser);
        GroupLog groupLog = groupLogRepository.save(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_REMOVED, user.getId(), "Member unsubscribed from group"));
        roleManagementService.removeGroupRolesFromUser(user, savedGroup);
        return savedGroup;
    }

    @Override
    public Group addRemoveGroupMembers(Group group, List<User> revisedUserList) {

        List<User> originalUsers = new ArrayList<>(group.getGroupMembers());

        log.info("These are the original users: " + originalUsers);

        // for some reason, the list remove function isn't working on these users, hence have to do this hard way

        for (User user : originalUsers) {
            if (!revisedUserList.contains(user)) {
                log.info("Removing a member: " + user);
                removeGroupMember(group, user);
            }
        }

        for (User user : revisedUserList) {
            if (!originalUsers.contains(user)) {
                log.info("Adding a member: " + user);
                addGroupMember(group, user);
            }
        }

        return saveGroup(group,false,"",dontKnowTheUser);
    }

    @Override
    public Group addNumberToGroup(Long groupId, String phoneNumber) {
        return addGroupMember(loadGroup(groupId), userManager.loadOrSaveUser(phoneNumber));
    }

    @Override
    public Group createNewGroup(User creatingUser, String groupName) {
        Long timeStart = System.currentTimeMillis();
        Group group = groupRepository.save(new Group(groupName, creatingUser));
        GroupLog groupLog = groupLogRepository.save(new GroupLog(group.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED,0L));
        Long timeEnd = System.currentTimeMillis();
        log.info(String.format("Creating a group without roles, time taken ... %d msecs", timeEnd - timeStart));
        return group;
    }

    /*@Override
    public Group createNewGroupWithRole(User creatingUser, String groupName) {
        Long timeStart = System.currentTimeMillis();
        Group group = createNewGroup(creatingUser, groupName);
        addGroupMember(group, creatingUser);
        roleManagementService.addDefaultRoleToGroupAndUser(BaseRoles.ROLE_GROUP_ORGANIZER, group, creatingUser);
        Long timeEnd = System.currentTimeMillis();
        log.info(String.format("Creating a group with roles, overall time taken ... %d msecs", timeEnd - timeStart));
        return group;
    }*/

    @Override
    public Group addGroupMember(Group currentGroup, User newMember) {

        // todo: just make sure this works as planned, if user has persisted in interim (e.g., maybe call repo?).
        if (currentGroup.getGroupMembers().contains(newMember)) {
            return currentGroup;
        } else {
            // todo: consider putting some of the persistence roles in the async part of things

            currentGroup.addMember(newMember);
            if (hasDefaultLanguage(currentGroup) && !newMember.isHasInitiatedSession())
                assignDefaultLanguage(currentGroup, newMember);

            currentGroup = saveGroup(currentGroup,false,"",dontKnowTheUser);
            newMember = userManager.save(newMember); // so that this is isntantly double-sided, else getting access control errors
            GroupLog groupLog = groupLogRepository.save(new GroupLog(currentGroup.getId(),dontKnowTheUser,GroupLogType.GROUP_MEMBER_ADDED,newMember.getId()));
            jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(currentGroup,newMember));

            return currentGroup;
        }
    }

    @Override
    public Group addGroupMemberWithDefaultRole(Group group, User user, String role) {
        group = addGroupMember(group, user);
        roleManagementService.addDefaultRoleToGroupAndUser(role, group, user);
        return group;
    }

    @Override
    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers) {
        return createNewGroup(userManager.getUserById(creatingUserId), phoneNumbers);
    }

    // bugs!
    @Override
    public Group createNewGroupWithCreatorAsMember(User creatingUser, String groupName) {
        Group group = new Group(groupName, creatingUser);
        group.addMember(creatingUser);
        Group savedGroup = groupRepository.save(group);
        GroupLog groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED,0L));
        return savedGroup;
    }

    @Override
    public Group createNewGroup(User creatingUser, List<String> phoneNumbers) {

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        Group groupToCreate = new Group();

        groupToCreate.setCreatedByUser(creatingUser);
        groupToCreate.setGroupName(""); // column not-null, so use blank string as default

        List<User> groupMembers = userManager.getUsersFromNumbers(phoneNumbers);
        groupMembers.add(creatingUser);
        groupToCreate.setGroupMembers(groupMembers);
        Group savedGroup = groupRepository.save(groupToCreate);
        GroupLog groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED,0L));
        for (User user : savedGroup.getGroupMembers()) {
            groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),creatingUser.getId(),GroupLogType.GROUP_MEMBER_ADDED,user.getId()));
        }
        return savedGroup;

    }

    @Override
    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers) {

        Group groupToExpand = loadGroup(groupId);
        log.info("ZOG: Adding numbers to group ... these numbers ... " + phoneNumbers + " ... to this group: " + groupToExpand);
        List<User> groupNewMembers = userManager.getUsersFromNumbers(phoneNumbers);

        for (User newMember : groupNewMembers) {
            groupToExpand.addMember(newMember);
        }

        log.info("ZOG: Group members now looks like .. " + groupToExpand.getGroupMembers());
        Group savedGroup = groupRepository.save(groupToExpand);
        for (User newMember : groupNewMembers) {
            GroupLog groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),dontKnowTheUser,GroupLogType.GROUP_MEMBER_ADDED,newMember.getId()));
            jmsTemplateProducerService.sendWithNoReply(EventChangeType.USER_ADDED.toString(),new NewGroupMember(groupToExpand,newMember));
        }

        return savedGroup;

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
    public boolean canGroupBeSetInactive(Group group, User user) {
        // todo: checking of permissions, etc etc
        return !(group.hasName());
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

    @Override
    public List<Group> groupsOnWhichCanCallVote(User user) {
        // major todo: integrate this with group/user permissions; for now, just returning all groups
        return getActiveGroupsPartOf(user);
    }

    /* @Override
    public boolean canUserCallVoteOnAnyGroup(User user) {
        return (groupsOnWhichCanCallVote(user).size() != 0);
    }*/

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUserAndActive(creatingUser, true);
    }

    @Override
    public List<Group> getGroupsPartOf(User sessionUser) {
        return groupRepository.findByGroupMembers(sessionUser);
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
    public List<Group> getListGroupsFromLogbooks(List<LogBook> logBooks) {
        // seems like doing it this way more efficient than running lots of group fetch queries, but need to test/verify
        log.info("Got a list of logbooks ... look like this: " + logBooks);
        List<Long> ids = new ArrayList<>();
        for (LogBook entry : logBooks) { ids.add(entry.getGroupId()); }
        log.info("And now we have this list of Ids ... " + ids);
        return groupRepository.findAllByIdIn(ids);
    }

    @Override
    public List<Group> findDiscoverableGroups(String groupName) {
        return groupRepository.findByGroupNameContainingAndDiscoverable(groupName, true);
    }

    @Override
    public boolean hasActiveGroupsPartOf(User user) {
        return !getPageOfActiveGroups(user, 0, 1).getContent().isEmpty();
    }

    /*@Override
    public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize) {
        return getPageOfGroups(sessionUser, pageNumber, pageSize).getContent();
    }

    @Override
    public Page<Group> getPageOfGroups(User sessionUser, int pageNumber, int pageSize) {
        return groupRepository.findByGroupMembers(sessionUser, new PageRequest(pageNumber, pageSize));
    }*/

    @Override
    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize) {
        return groupRepository.findByGroupMembersAndActive(sessionUser, new PageRequest(pageNumber, pageSize), true);
    }

    /*
    We use this for the web home page, so we can show a structured set of groups. Another way to do this might be via
    a query in the repository, but the most obvious, findByUserAndParentNull, won't quite work if a user is a member
     of a subgroup but not of the parent (i.e., the group is seniormost for that user but not within that tree
     todo: figure out how to do this via query, or the below implementation may kill us when the group lists are large
     */

    @Override
    public List<Group> getActiveTopLevelGroups(User user) {

        List<Group> groupsPartOf = getActiveGroupsPartOf(user);
        List<Group> topLevelGroups = new ArrayList<>();

        for (Group group : groupsPartOf) {
            if (group.getParent() == null || !isUserInGroup(group.getParent(), user)) {
                topLevelGroups.add(group);
            }
        }

        return topLevelGroups;
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
    public Group getGroupByToken(String groupToken) {
        Group groupToReturn = groupRepository.findByGroupTokenCode(groupToken);
        if (groupToReturn == null) return null;
        if (groupToReturn.getTokenExpiryDateTime().before(Timestamp.valueOf(LocalDateTime.now()))) return null;
        return groupToReturn;
    }

    @Override
    public Group generateGroupToken(Long groupId) {
        return generateGroupToken(loadGroup(groupId));
    }

    @Override
    public Group generateGroupToken(Group group) {
        log.info("Generating a token code that is indefinitely open, within postgresql range ... We will have to adjust this before the next century");
        Timestamp endOfCentury = Timestamp.valueOf(LocalDateTime.of(2099, 12, 31, 23, 59));
        group.setGroupTokenCode(generateCodeString());
        group.setTokenExpiryDateTime(endOfCentury);
        return saveGroup(group,true,String.format("Set Group Token: %s",group.getGroupTokenCode()),dontKnowTheUser);
    }

    @Override
    public Group generateGroupToken(Group group, Integer daysValid) {
        // todo: checks for whether the code already exists, and/or existing validity of group

        log.info("Generating a new group token, for group: " + group.getId());
        Group groupToReturn;
        if (daysValid == 0) {
            groupToReturn = generateGroupToken(group);
        } else {
            Integer daysMillis = 24 * 60 * 60 * 1000;
            Timestamp expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + daysValid * daysMillis);

            group.setGroupTokenCode(generateCodeString());
            group.setTokenExpiryDateTime(expiryDateTime);

            log.info("Group code generated: " + group.getGroupTokenCode());

            groupToReturn = saveGroup(group,true,String.format("Set Group Token: %s",group.getGroupTokenCode()),dontKnowTheUser);

            log.info("Group code after save: " + group.getGroupTokenCode());
        }

        return groupToReturn;
    }

    @Override
    public Group generateGroupToken(Long groupId, Integer daysValid) {
        return generateGroupToken(loadGroup(groupId), daysValid);
    }

    @Override
    public Group extendGroupToken(Group group, Integer daysExtension) {
        Integer daysMillis = 24 * 60 * 60 * 1000; // need to put this somewhere else so not copying & pasting
        Timestamp newExpiryDateTime = new Timestamp(group.getTokenExpiryDateTime().getTime() + daysExtension * daysMillis);
        group.setTokenExpiryDateTime(newExpiryDateTime);
        return saveGroup(group,true,String.format("Extend group toke %s to %s",group.getGroupTokenCode(),newExpiryDateTime.toString()),dontKnowTheUser);
    }

    @Override
    public Group invalidateGroupToken(Group group) {
        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        group.setGroupTokenCode(null); // alternately, set it to ""
        return saveGroup(group,true,"Invalidate Group Token",dontKnowTheUser);
    }

    @Override
    public Group invalidateGroupToken(Long groupId) {
        return invalidateGroupToken(loadGroup(groupId));
    }

    @Override
    public boolean groupHasValidToken(Long groupId) {
        return groupHasValidToken(loadGroup(groupId));
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
    public Group setGroupDiscoverable(Long groupId, boolean discoverable, User user) {
        // todo: create a dedicated permission for this, and uncomment, when we have permission setting working on group create
        // todo: once we have implemented 'request to join', will need to wire that up here
        return setGroupDiscoverable(loadGroup(groupId), discoverable, user.getId());
    }

    @Override
    public Group setGroupDiscoverable(Group group, boolean discoverable, Long userId) {
        if (group.isDiscoverable() == discoverable) return group;
        String logEntry = discoverable ? "Set group publicly discoverable" : "Set group hidden from public";
        group.setDiscoverable(discoverable);
        return saveGroup(group, true, logEntry, userId);
    }

    @Override
    public boolean canUserModifyGroup(Group group, User user) {
        Permission permission = permissionsManager.findByName(BasePermissions.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        return accessControlService.hasGroupPermission(permission, group, user);
    }

    private String generateCodeString() {
        // todo: implement a unique code generating algorithm that actually makes sense
        //return String.valueOf(1000 + new Random().nextInt(9999));
        return String.valueOf(tokenGeneratorService.getNextToken());
    }

    /*
    returns the new sub-group
     */
    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName) {
        return createSubGroup(userManager.getUserById(createdByUserId), loadGroup(groupId), subGroupName);
    }

    public Group createSubGroup(User createdByUser, Group group, String subGroupName) {
        Group subGroup = groupRepository.save(new Group(subGroupName, createdByUser, group));
        GroupLog groupLog = groupLogRepository.save(new GroupLog(group.getId(),createdByUser.getId(),GroupLogType.SUBGROUP_ADDED,subGroup.getId(),String.format("Sub Group: %s added",subGroupName)));
        return subGroup;
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

    /*
    The method checks whether the
     */
    @Override
    public boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup) {
        for (Group g : getAllParentGroups(possibleParentGroup)) {
            // if this returns true, then the group being passed as child is already in the parent chain of the desired
            // parent, which will create an infinite loop, hence prevent it
            if (g.getId() == possibleChildGroup.getId()) return true;
        }
        return false;
    }

    @Override
    public Group setGroupDefaultReminderMinutes(Group group, Integer minutes) {
        group.setReminderMinutes(minutes);
        return saveGroup(group,true,String.format("Set reminder minutes to %d",minutes),dontKnowTheUser);
    }

    @Override
    public Group setGroupDefaultReminderMinutes(Long groupId, Integer minutes) {
        return setGroupDefaultReminderMinutes(loadGroup(groupId), minutes);
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
                userManager.setUserLanguage(user, locale);
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
        userManager.setUserLanguage(user, group.getDefaultLanguage());
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
        Event lastEvent = eventRepository.findFirstByAppliesToGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        if (lastEvent == null)
            return getLastTimeGroupModified(group);
        else
            return lastEvent.getEventStartDateTime().toLocalDateTime();
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
    public Group setGroupInactive(Group group) {
        // todo errors and exception throwing
        group.setActive(false);
        Group savedGroup = groupRepository.save(group);
        GroupLog groupLog = groupLogRepository.save(new GroupLog(group.getId(),dontKnowTheUser,GroupLogType.GROUP_REMOVED,0L,String.format("Set group inactive")));
        return savedGroup;
    }

    @Override
    public Group setGroupInactive(Long groupId) {
        return setGroupInactive(loadGroup(groupId));
    }

    @Override
    public Group mergeGroups(Long firstGroupId, Long secondGroupId) {
        log.info("Okay, trying to merge these groups");
        return mergeGroups(loadGroup(firstGroupId), loadGroup(secondGroupId));
    }

    @Override
    public Group mergeGroupsLeaveActive(Long firstGroupId, Long secondGroupId) {
        return mergeGroups(loadGroup(firstGroupId), loadGroup(secondGroupId), false);
    }

    @Override
    public Group mergeGroupsIntoNew(Long firstGroupId, Long secondGroupId, String newGroupName, User creatingUser) {
        Group consolidatedGroup = new Group(newGroupName, creatingUser);
        Set<User> setOfMembers = new HashSet<>(loadGroup(firstGroupId).getGroupMembers());
        setOfMembers.addAll(loadGroup(secondGroupId).getGroupMembers());
        consolidatedGroup.setGroupMembers(new ArrayList<>(setOfMembers));
        Group savedGroup = saveGroup(consolidatedGroup,true,String.format("Merged group %d with %d",secondGroupId,firstGroupId),creatingUser.getId());
        for (User u : setOfMembers) {
            GroupLog groupLog = groupLogRepository.save(new GroupLog(savedGroup.getId(),creatingUser.getId(),GroupLogType.GROUP_MEMBER_ADDED,u.getId()));
        }
        return savedGroup;
    }

    @Override
    public Group mergeGroups(Group groupA, Group groupB) {
        return mergeGroups(groupA, groupB, true);
    }

    @Override
    public Group mergeGroups(Group groupA, Group groupB, boolean setConsolidatedGroupInactive) {

        Group largerGroup, smallerGroup;

        // note: if the groups are the same size, this will default to merging into the first-passed group
        if (groupA.getGroupMembers().size() >= groupB.getGroupMembers().size()) {
            largerGroup = groupA;
            smallerGroup = groupB;
        } else {
            largerGroup = groupB;
            smallerGroup = groupA;
        }

        return mergeGroupsSpecifyOrder(largerGroup, smallerGroup, setConsolidatedGroupInactive);
    }

    @Override
    public Group mergeGroupsSpecifyOrder(Group groupInto, Group groupFrom, boolean setFromGroupInactive) {

        // todo: optimize this, almost certainly very slow
        for (User user : new ArrayList<>(groupFrom.getGroupMembers())) {
            addGroupMember(groupInto, user);
        }

        groupFrom.setActive(!setFromGroupInactive);
        saveGroup(groupFrom,true,String.format("Set group %d inactive",groupFrom.getId()),dontKnowTheUser);

        return saveGroup(groupInto,true,String.format("Merged group %d into %d",groupFrom.getId(),groupInto.getId()),dontKnowTheUser);
    }

    @Override
    public Group mergeGroupsSpecifyOrder(Long groupIntoId, Long groupFromId, boolean setFromGroupInactive) {
        return mergeGroupsSpecifyOrder(loadGroup(groupIntoId), loadGroup(groupFromId), setFromGroupInactive);
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
    public String getGroupName(Long groupId) {
        return loadGroup(groupId).getName("");
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
