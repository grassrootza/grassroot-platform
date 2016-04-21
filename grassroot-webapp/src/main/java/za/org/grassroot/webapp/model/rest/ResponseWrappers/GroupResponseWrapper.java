package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.util.Set;


/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper {

    private String id;
    private String groupName;
    private String description = "Group has no event.";
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;
    private LocalDateTime dateTime;
    private Set<Permission> permissions;
    private static final String filterString ="CREATE";

    public GroupResponseWrapper(){}

    public GroupResponseWrapper(Group group, Event event, Role role){

        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions(),filterString);

    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role){
        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = groupLog.getDescription();
        this.dateTime = groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions(),filterString);


    }


    public GroupResponseWrapper(Group group, Role role){
        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions(), filterString);

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
    public LocalDateTime getDateTime(){return dateTime;}




}
