package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;

import javax.transaction.Transactional;
import java.util.List;

/**
 * @author luke on 2015/08/14.
 * todo: add a "getlastgroupcreated" method, which we will use a lot
 */

@Service
@Transactional
public class GroupManager implements GroupManagementService {

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserManagementService userManager;

    /**
     * Have not yet created methods analogous to those in UserManager, as not sure if necessary
     * For the moment, using this to expose some basic group services for the application interfaces
     */

    @Override
    public Group loadGroup(Long groupId) {
        return groupRepository.findOne(groupId);
    }

    @Override
    public Group saveGroup(Group groupToSave) {
        return groupRepository.save(groupToSave);
    }

    @Override
    public void deleteGroup(Group groupToDelete) {
        groupRepository.delete(groupToDelete);
    }

    @Override
    public Group createNewGroup(User creatingUser, String phoneNumbers) {

        // todo: consider some way to check if group "exists", needs a solid "equals" logic
        // todo: defaulting to using Lists as Collection type for many-many, but that's an amateur decision ...

        Group groupToCreate = new Group();

        groupToCreate.setCreatedByUser(creatingUser);
        groupToCreate.setGroupName(""); // column not-null, so use blank string as default

        List<User> usersToCreateGroup = userManager.getUsersFromNumbers(phoneNumbers);
        usersToCreateGroup.add(creatingUser); // So that later actions pick up whoever created group
        groupToCreate.setGroupMembers(usersToCreateGroup);

        return groupRepository.save(groupToCreate);

    }

    @Override
    public Group getLastCreatedGroup(User creatingUser) {
        return groupRepository.findFirstByCreatedByUserOrderByIdDesc(creatingUser);
    }

    @Override
    public List<Group> getCreatedGroups(User creatingUser) {
        return groupRepository.findByCreatedByUser(creatingUser);
    }

}
