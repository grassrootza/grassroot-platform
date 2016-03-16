package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;

import java.sql.Timestamp;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper {

    private String id;
    private String groupName;
    private String description = "Group has no event";
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;
    private Timestamp timestamp;
    private Set<Permission> permissions;

    public GroupResponseWrapper(){}

    public GroupResponseWrapper(Group group){
        this.id =group.getUid();
        this.groupName = group.getGroupName();
    }

    public GroupResponseWrapper(Group group, Event event, Role role){

        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = event.getName();
        this.timestamp = event.getEventStartDateTime();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = role.getName();
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = filterPermissions(role.getPermissions());

    }


    public GroupResponseWrapper(Group group, Role role){
        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = role.getName();
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = filterPermissions(role.getPermissions());

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
    public Timestamp getTimestamp(){return timestamp;}


    private Set<Permission> filterPermissions(Set<Permission> permissions){
        return permissions.stream().filter(p -> p.toString().contains("CREATE")).collect(Collectors.toSet());

    }

}
