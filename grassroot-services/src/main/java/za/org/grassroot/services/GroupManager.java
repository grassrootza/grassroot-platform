package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.services.util.TokenGeneratorService;

import javax.jws.soap.SOAPBinding;
import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Random;

/**
 * @author luke on 2015/08/14.
 *
 */

@Service
@Transactional
public class GroupManager implements GroupManagementService {

    Logger log = LoggerFactory.getLogger(GroupManager.class);

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserManagementService userManager;

    @Autowired
    TokenGeneratorService tokenGeneratorService;

    /**
     * Have not yet created methods analogous to those in UserManager, as not sure if necessary
     * For the moment, using this to expose some basic group services for the application interfaces
     */

    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public List<Group> getGroupsFromUser(User sessionUser) {
        // todo: add pagination
        return sessionUser.getGroupsPartOf();
    }

    @Override
    public Group saveGroup(Group groupToSave) {
        return groupRepository.save(groupToSave);
    }

    @Override
    public void deleteGroup(Group groupToDelete) {
        groupRepository.delete(groupToDelete);
    }


    //todo aakil send event notification to new group member
    @Override
    public Group addGroupMember(Long currentGroupId, Long newMemberId) {
        return addGroupMember(getGroupById(currentGroupId), userManager.getUserById(newMemberId));
    }
    @Override
    public Group addGroupMember(Group currentGroup, User newMember) {
        // todo: just make sure this works as planned, if user has persisted in interim (e.g., maybe call repo?).
        if (currentGroup.getGroupMembers().contains(newMember)) {
            return currentGroup;
        } else {
            currentGroup.addMember(newMember);
            return groupRepository.save(currentGroup);
        }
    }

    @Override
    public Group createNewGroup(Long creatingUserId, List<String> phoneNumbers) {
        return createNewGroup(userManager.getUserById(creatingUserId), phoneNumbers);
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

        return groupRepository.save(groupToCreate);

    }

    //todo aakil send event notification to new group member
    @Override
    public Group addNumbersToGroup(Long groupId, List<String> phoneNumbers) {

        Group groupToExpand = loadGroup(groupId);
        List<User> groupNewMembers = userManager.getUsersFromNumbers(phoneNumbers);

        for (User newMember : groupNewMembers)
            groupToExpand.addMember(newMember);

        return groupRepository.save(groupToExpand);

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
        Group lastCreatedGroup = getLastCreatedGroup(sessionUser);
        return (lastCreatedGroup != null && !lastCreatedGroup.hasName());
    }

    @Override
    public Long groupToRename(User sessionUser) {
        return getLastCreatedGroup(sessionUser).getId();
    }

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUser(creatingUser);
    }

    @Override
    public List<Group> getGroupsPartOf(User sessionUser) {
        return groupRepository.findByGroupMembers(sessionUser);
    }

    @Override
    public List<Group> getPaginatedGroups(User sessionUser, int pageNumber, int pageSize) {
        Page<Group> pageOfGroups = groupRepository.findByGroupMembers(sessionUser, new PageRequest(pageNumber, pageSize));
        return pageOfGroups.getContent();
    }

    @Override
    public List<Group> getSubGroups(Group group) {
        return groupRepository.findByParent(group);
    }

    @Override
    public Group getGroupById(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public Group getGroupByToken(String groupToken) {
        return groupRepository.findByGroupTokenCode(groupToken);
    }

    @Override
    public Group generateGroupToken(Group group, Integer daysValid) {
        // todo: checks for whether the code already exists, and/or existing validity of group

        log.info("Generating a new group token, for group: " + group.getId());

        Integer daysMillis = 24 * 60 * 60 * 1000;
        Timestamp expiryDateTime = new Timestamp(Calendar.getInstance().getTimeInMillis() + daysValid * daysMillis);

        group.setGroupTokenCode(generateCodeString());
        group.setTokenExpiryDateTime(expiryDateTime);

        log.info("Group code generated: " + group.getGroupTokenCode());

        group = groupRepository.save(group);

        log.info("Group code after save: " + group.getGroupTokenCode());

        return group;
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
        return groupRepository.save(group);
    }

    @Override
    public Group invalidateGroupToken(Group group) {
        group.setTokenExpiryDateTime(new Timestamp(Calendar.getInstance().getTimeInMillis()));
        group.setGroupTokenCode(null); // alternately, set it to ""
        return groupRepository.save(group);
    }

    @Override
    public Group invalidateGroupToken(Long groupId) {
        return invalidateGroupToken(loadGroup(groupId));
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

    private String generateCodeString() {
        // todo: implement a unique code generating algorithm that actually makes sense
        //return String.valueOf(1000 + new Random().nextInt(9999));
        return String.valueOf(tokenGeneratorService.getNextToken());
    }

    /*
    returns the new sub-group
     */
    public Group createSubGroup(Long createdByUserId, Long groupId, String subGroupName) {
        return createSubGroup(userManager.getUserById(createdByUserId), loadGroup(groupId),subGroupName);
    }

    public Group createSubGroup(User createdByUser, Group group, String subGroupName) {
        return groupRepository.save(new Group(subGroupName,createdByUser,group));
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Long groupId) {
        return getAllUsersInGroupAndSubGroups(loadGroup(groupId));
    }

    @Override
    public List<User> getAllUsersInGroupAndSubGroups(Group group) {
        List<User> userList = new ArrayList<User>();
        recursiveUserAdd(group,userList);
        return userList;
    }

    private void recursiveUserAdd(Group parentGroup, List<User> userList ) {

        for (Group childGroup : groupRepository.findByParent(parentGroup)) {
            recursiveUserAdd(childGroup,userList);
        }

        // add all the users at this level
        userList.addAll(parentGroup.getGroupMembers());

    }
}
