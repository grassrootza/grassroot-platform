package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.io.Serializable;

/**
 * Created by aakilomar on 9/26/15.
 */
public class NewGroupMember implements Serializable {

    private Group group;
    private User newMember;

    public NewGroupMember(Group group, User newMember) {
        this.group = group;
        this.newMember = newMember;
    }

    public Group getGroup() {
        return group;
    }

    public void setGroup(Group group) {
        this.group = group;
    }

    public User getNewMember() {
        return newMember;
    }

    public void setNewMember(User newMember) {
        this.newMember = newMember;
    }

    @Override
    public String toString() {
        return "NewGroupMember{" +
                "group=" + group +
                ", newMember=" + newMember +
                '}';
    }
}
