package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.util.TokenGeneratorService;

import javax.transaction.Transactional;
import java.security.Permissions;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

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
    private GroupBroker groupBroker;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
    private UserManagementService userManager;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
    private PermissionsManagementService permissionsManager;

    @Autowired
    private GroupAccessControlManagementService accessControlService;

    @Autowired
    private AsyncGroupService asyncGroupService;

    @Autowired
    private AsyncRoleService asyncRoleService;

/*    @Override
    public Group createNewGroup(User creatingUser, String groupName, boolean addDefaultRole) {
        Long timeStart = System.currentTimeMillis();
        Group group = groupRepository.save(new Group(groupName, creatingUser));

        if (addDefaultRole) { group.setGroupRoles(roleManagementService.createGroupRoles(group.getUid())); }

        group = groupRepository.saveAndFlush(group);
        Long timeEnd = System.currentTimeMillis();
        log.info(String.format("Setting up a standard group, time taken ... %d msecs", timeEnd - timeStart));
        asyncGroupService.recordGroupLog(group.getId(),creatingUser.getId(),GroupLogType.GROUP_ADDED, 0L, "");;
        return group;
    }*/

    @Override
    public Group saveGroup(Group groupToSave, boolean createGroupLog, String description, Long changedByuserId) {
        Group group = groupRepository.save(groupToSave);
        if (createGroupLog)
            asyncGroupService.recordGroupLog(groupToSave.getId(),changedByuserId, GroupLogType.GROUP_UPDATED,0L,description);
        return group;
    }

    @Override
    public Group renameGroup(String groupUid, String newName, String changingUserUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        if (!group.getGroupName().equals(newName)) {
            String oldName = group.getGroupName();
            group.setGroupName(newName);
            return saveGroup(group,true,String.format("Old name: %s, New name: %s",oldName,newName),dontKnowTheUser);
        } else {
            return group;
        }
    }

    /*
    SECTION: Loading groups, finding properties, etc
     */


    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public Group loadGroupByUid(String uid) {
        return groupRepository.findOneByUid(uid);
    }

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUserAndActiveOrderByCreatedDateTimeDesc(creatingUser, true);
    }

    @Override
    public List<Group> getActiveGroupsPartOf(User sessionUser) {
        return groupRepository.findByMembershipsUserAndActive(sessionUser, true);
    }

    @Override
    public List<GroupDTO> getActiveGroupsPartOfOrderedByRecent(User sessionUser) {
        List<Object[]> listObjArray =  groupRepository.findActiveUserGroupsOrderedByRecentEvent(sessionUser.getId());
        List<GroupDTO> list = new ArrayList<>();
        for (Object[] objArray : listObjArray) {
            list.add(new GroupDTO(objArray));
        }
        return list;
    }

    @Override
    public Page<Group> getPageOfActiveGroups(User sessionUser, int pageNumber, int pageSize) {
        return groupRepository.findByMembershipsUserAndActive(sessionUser, new PageRequest(pageNumber, pageSize), true);
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
        return groupRepository.countByIdAndMembershipsUser(group.getId(), user) > 0;
    }

    /*
    Methods to implement finding last group and prompting to rename if unnamed. May make this a little more complex
    in future (e.g., check not just last created group, but any unnamed created groups above X users, or so on). Hence
    a little duplication / redundancy for now.
     */

    @Override
    public Group groupToRename(User sessionUser) {
        Group lastCreatedGroup = groupRepository.findFirstByCreatedByUserOrderByIdDesc(sessionUser);
        if (lastCreatedGroup != null && lastCreatedGroup.isActive() && !lastCreatedGroup.hasName())
            return lastCreatedGroup;
        else
            return null;
    }

    @Override
    public boolean hasActiveGroupsPartOf(User user) {
        // return !getActiveGroupsPartOf(user).isEmpty();
        return groupRepository.countByMembershipsUserAndActiveTrue(user) > 0;
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
    public Group generateGroupToken(String groupUid, String generatingUserUid) {
        log.info("Generating a token code that is indefinitely open, within postgresql range ... We will have to adjust this before the next century");
        Group group = groupRepository.findOneByUid(groupUid);
        User generatingUser = userManager.loadUserByUid(generatingUserUid); // todo: leave out once groupLogs restructured to Uid
        final Timestamp endOfCentury = Timestamp.valueOf(LocalDateTime.of(2099, 12, 31, 23, 59));
        group.setGroupTokenCode(generateCodeString());
        group.setTokenExpiryDateTime(endOfCentury);
        return saveGroup(group, true, String.format("Set Group Token: %s",group.getGroupTokenCode()), generatingUser.getId());
    }

    @Override
    public Group generateExpiringGroupToken(String groupUid, String userUid, Integer daysValid) {
        Group group = groupRepository.findOneByUid(groupUid);
        log.info("Generating a new group token, for group: " + group.getId());
        Group groupToReturn;
        if (daysValid == 0) {
            groupToReturn = generateGroupToken(groupUid, userUid);
        } else {
            Integer daysMillis = 24 * 60 * 60 * 1000;
            Timestamp expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + daysValid * daysMillis);

            group.setGroupTokenCode(generateCodeString());
            group.setTokenExpiryDateTime(expiryDateTime);

            log.info("Group code generated: " + group.getGroupTokenCode());

            // todo: pass User UID to the log call
            groupToReturn = saveGroup(group,true,String.format("Set Group Token: %s",group.getGroupTokenCode()),dontKnowTheUser);

            log.info("Group code after save: " + group.getGroupTokenCode());
        }

        return groupToReturn;
    }

    @Override
    public Group extendGroupToken(Group group, Integer daysExtension, User user) {
        Integer daysMillis = 24 * 60 * 60 * 1000; // need to put this somewhere else so not copying & pasting
        Timestamp newExpiryDateTime = new Timestamp(group.getTokenExpiryDateTime().getTime() + daysExtension * daysMillis);
        group.setTokenExpiryDateTime(newExpiryDateTime);
        return saveGroup(group,true,String.format("Extend group token %s to %s",group.getGroupTokenCode(),newExpiryDateTime.toString()),user.getId());
    }

    @Override
    public Group closeGroupToken(String groupUid, String closingUserUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        group.setGroupTokenCode(null); // alternately, set it to "" // todo: switch below to Uid
        return saveGroup(group,true,"Invalidate Group Token",dontKnowTheUser);
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
        asyncGroupService.recordGroupLog(group.getId(),createdByUser.getId(),GroupLogType.SUBGROUP_ADDED, subGroup.getId(),
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
        return userManager.getGroupMembersSortedById(loadGroup(groupId));
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
        asyncGroupService.recordGroupLog(parent.getId(),dontKnowTheUser,GroupLogType.SUBGROUP_ADDED,child.getId(),description);
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
        Set<User> userList = group.getMembers();

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
    public Integer getGroupSize(Group group, boolean includeSubGroups) {
        // as with a few above, a little trivial as implemented here, but may change in future, so rather here than code in webapp

        log.info("Getting group member size");
        if (!includeSubGroups) {
            log.info("Getting group size, for group: " + group);
            return group.getMembers().size();
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
    public Group setGroupInactive(Group group, User user) {
        // todo errors and exception throwing
        group.setActive(false);
        Group savedGroup = groupRepository.save(group);
        asyncGroupService.recordGroupLog(group.getId(),user.getId(),GroupLogType.GROUP_REMOVED,0L,String.format("Set group inactive"));
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
        Set<User> firstGroupMembers = loadGroup(firstGroupId).getMembers();
        Set<User> secondGroupMembers = loadGroup(secondGroupId).getMembers();

        Group consolidatedGroup = new Group(newGroupName, creatingUser);
        consolidatedGroup.addMembers(firstGroupMembers);
        consolidatedGroup.addMembers(secondGroupMembers);
        Group savedGroup = saveGroup(consolidatedGroup,true,String.format("Merged group %d with %d",secondGroupId,firstGroupId),creatingUser.getId());

        for (User u : firstGroupMembers) {
            asyncGroupService.addNewGroupMemberLogsMessages(savedGroup, u, creatingUser.getId());
            asyncRoleService.addRoleToGroupAndUser(BaseRoles.ROLE_ORDINARY_MEMBER, savedGroup, u, creatingUser);
        }

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
        if (groupA.getMembers().size() >= groupB.getMembers().size()) {
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

        // todo: notify user that roles will all transfer over (may want to not transfer "organizer"
        // todo: make this all cleaner & faster (the next four lines should be able to do in one or two)
        String mergingUserUid = userManager.getUserById(mergingUserId).getUid();
        String groupIntoUid = groupInto.getUid();
        Set<MembershipInfo> membersToTransfer = new HashSet<>();
        for (Membership member : groupFrom.getMemberships())
            membersToTransfer.add(new MembershipInfo(member.getUser().getPhoneNumber(), member.getRole().getName(),
                                                     member.getUser().getDisplayName()));

        groupBroker.addMembers(mergingUserUid, groupIntoUid, membersToTransfer);
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
        Integer group1size = loadGroup(groupId1).getMembers().size(),
                group2size = loadGroup(groupId2).getMembers().size();
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
        Predicate<Group> predicate = group -> {
            boolean createdByUserIncluded = createdByUser == null || group.getCreatedByUser().equals(createdByUser);
            boolean minGroupSizeIncluded = minGroupSize == null || group.getMembers().size() > minGroupSize;
            boolean createdAfterDateIncluded = createdAfterDate == null || group.getCreatedDateTime().after(createdAfterDate);
            boolean createdBeforeDateIncluded = createdBeforeDate == null || group.getCreatedDateTime().before(createdBeforeDate);
            return createdByUserIncluded && minGroupSizeIncluded && createdAfterDateIncluded && createdBeforeDateIncluded;
        };
        return allGroups.stream().filter(predicate).collect(Collectors.toList());
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
        userList.addAll(groupRepository.findOne(parentGroup.getId()).getMembers());

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
