package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;

import java.util.Set;

/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper {

    private String id;
    private String groupName;
    private String description;
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;
    private Set<Permission> permissions;

    public GroupResponseWrapper(){}

    public GroupResponseWrapper(Group group, Event event, Role role){

        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = event.getName();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = role.getName();
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = role.getPermissions();

    }

    public GroupResponseWrapper(Group group, Role role){
        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = role.getName();
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = role.getPermissions();

    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }

    public String getGroupName() {
        return groupName;
    }


    public String getGroupCreator() {
        return groupCreator;
    }
    public String getRole() {
        return role;
    }

    public Integer getGroupMemberCount() {
        return groupMemberCount;
    }
    public Set<Permission> getPermissions() {
        return permissions;
    }

}
