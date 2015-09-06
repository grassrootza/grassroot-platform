package za.org.grassroot.webapp.model.rest;

import za.org.grassroot.core.domain.Group;

import java.io.Serializable;

/**
 * Created by aakilomar on 9/5/15.
 */
public class GroupDTO implements Serializable {

    private final Long id;
    private final String groupName;

    public GroupDTO(Group group) {
        this.id =  group.getId();
        this.groupName = group.getGroupName();
    }

    public Long getId() {
        return id;
    }

    public String getGroupName() {
        return groupName;
    }

    @Override
    public String toString() {
        return "GroupDTO{" +
                "id=" + id +
                ", groupName='" + groupName + '\'' +
                '}';
    }
}
