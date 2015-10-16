package za.org.grassroot.webapp.model.web;

import org.springframework.beans.factory.annotation.Autowired;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.GroupManagementService;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by luke on 2015/10/16.
 */
public class GroupViewNode {

    @Autowired
    GroupManagementService groupManagementService;

    private Group group;
    private User viewingUser;

    private GroupViewNode parent;
    private List<GroupViewNode> subgroups;

    private String groupName;
    private boolean isTopLevelGroup;
    private boolean hasSubGroups;

    private Integer groupMembers;

    public GroupViewNode(Group group, User viewingUser) {

        this.group = group;
        this.viewingUser = viewingUser;
        this.groupName = group.getGroupName();
        this.groupMembers = groupManagementService.getGroupSize(group, false);

        Group possibleParent = groupManagementService.getParent(group);

        if (possibleParent != null && groupManagementService.isUserInGroup(possibleParent, viewingUser)) {
            this.parent = new GroupViewNode(possibleParent, viewingUser);
            isTopLevelGroup = false;
        } else {
            this.parent = null;
            isTopLevelGroup = true;
        }

        subgroups = new ArrayList<>();
        List<Group> allSubGroups = groupManagementService.getSubGroups(group);

        for (Group possibleChild : allSubGroups) {
            if (groupManagementService.isUserInGroup(possibleChild, viewingUser))
                subgroups.add(new GroupViewNode(possibleChild, viewingUser));
        }

        this.hasSubGroups = (subgroups.size() != 0);

    }

}
