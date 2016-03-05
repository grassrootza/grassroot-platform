package za.org.grassroot.webapp.model.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupManagementService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2015/10/16.
 */
public class GroupViewNode {

    private static final Logger log = LoggerFactory.getLogger(GroupViewNode.class);

    private Group group;
    private User viewingUser;

    private GroupManagementService groupManagementService;

    // private GroupViewNode parent;
    private List<GroupViewNode> subgroups;

    private String groupName;
    private boolean isTopLevelGroup;
    private boolean hasSubGroups;

    private Integer numberMembers;

    // this is somewhat ugly, but need it until can figure out how to count recursions in Thymeleaf
    private Integer level;

    /*
    Constructor
     */
    public GroupViewNode(Group group, User viewingUser, GroupManagementService groupManagementService, Integer priorLevel) {
        Long startTime = System.currentTimeMillis();
        log.info("Creating a view node from this group: " + group);

        this.group = group;
        this.viewingUser = viewingUser;
        this.groupManagementService = groupManagementService;

        this.groupName = group.getGroupName();

        this.numberMembers = groupManagementService.getGroupSize(group.getId(), false);
        Group possibleParent = group.getParent();

        if (possibleParent != null && groupManagementService.isUserInGroup(possibleParent, viewingUser)) {
            // this.parent = new GroupViewNode(possibleParent, viewingUser, groupManagementService); // think this creates infinite loop
            isTopLevelGroup = false;
            this.level = priorLevel + 1;
        } else {
            isTopLevelGroup = true;
            this.level = 0;
        }

        subgroups = new ArrayList<>();
        List<Group> allSubGroups = groupManagementService.getSubGroups(group);

        for (Group possibleChild : allSubGroups) {
            if (groupManagementService.isUserInGroup(possibleChild, viewingUser))
                subgroups.add(new GroupViewNode(possibleChild, viewingUser, groupManagementService, this.level));
        }

        this.hasSubGroups = (subgroups.size() != 0);
        Long endTime = System.currentTimeMillis();

        log.info(String.format("GroupViewNode... timetaken... %d msec", endTime - startTime) );

    }

    public GroupViewNode(Group group, User viewingUser, GroupManagementService groupManagementService) {

        this(group, viewingUser, groupManagementService, 0);

    }

    /*
    Methods for traversing the tree
     */

    public boolean hasChildren() {
        return hasSubGroups;
    }

    public boolean isTerminal() {
        return !hasSubGroups;
    }

    public List<GroupViewNode> getSubgroups() {
        return subgroups;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean isTopLevelGroup() {
        return isTopLevelGroup;
    }

    public Integer getNumberMembers() {
        return numberMembers;
    }

    public Integer getLevel() {
        return level;
    }
}
