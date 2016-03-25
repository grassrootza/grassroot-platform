package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.GroupDTO;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;
import za.org.grassroot.services.util.TokenGeneratorService;

import javax.transaction.Transactional;
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
    private UserManagementService userManager;

    @Autowired
    private GroupLogRepository groupLogRepository;

    /*
    SECTION: Loading groups, finding properties, etc
     */

    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
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
        for (LogBook entry : logBooks) { ids.add(entry.getGroup().getId()); }
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

    /*
    Methods to work with group joining tokens and group discovery
     */

    @Override
    public Group findGroupByToken(String groupToken) {
        Group groupToReturn = groupRepository.findByGroupTokenCode(groupToken);
        if (groupToReturn == null) return null;
        if (groupToReturn.getTokenExpiryDateTime().before(Timestamp.valueOf(LocalDateTime.now()))) return null;
        return groupToReturn;
    }

    @Override
    public boolean groupHasValidToken(Group group) {

        boolean codeExists = group.getGroupTokenCode() != null && group.getGroupTokenCode().trim() != "";
        boolean codeValid = group.getTokenExpiryDateTime() != null &&
                group.getTokenExpiryDateTime().after(new Timestamp(Calendar.getInstance().getTimeInMillis()));

        return codeExists && codeValid;

    }

    /**
     * Methods for working with subgroups
     */

    @Override
    public List<Group> getSubGroups(Group group) {
        return groupRepository.findByParent(group);
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
    public Group linkSubGroup(Group child, Group parent) {
        // todo: error checking, for one more barrier against infintite loops
        child.setParent(parent);
        Group savedChild = groupRepository.save(child);
        /*
        Bit of reversed logic therefore not putting it in saveGroup
         */
        String description = String.format("Linked group: %s to %s",child.getGroupName().trim().equals("") ? child.getId() : child.getGroupName(),
                parent.getGroupName().trim().equals("") ? parent.getId() : parent.getGroupName());
        groupLogRepository.save(new GroupLog(parent.getId(),dontKnowTheUser,GroupLogType.SUBGROUP_ADDED,child.getId(),description));
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
    public Integer getGroupSize(Long groupId, boolean includeSubGroups) {
        // as with a few above, a little trivial as implemented here, but may change in future, so rather here than code in webapp

        Group group = groupRepository.findOne(groupId);
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
    public List<Group> getMergeCandidates(User mergingUser, Long firstGroupSelected) {

        // todo: lots of error handling etc
        List<Group> createdGroups = getCreatedGroups(mergingUser);
        createdGroups.remove(loadGroup(firstGroupSelected));
        return createdGroups;
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
