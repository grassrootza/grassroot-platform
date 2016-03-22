package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.webapp.util.RestUtil;

import java.sql.Timestamp;
import java.time.Instant;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Set;
import java.util.TimeZone;
import java.util.stream.Collectors;


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
    private Timestamp dateTime;
    private Set<Permission> permissions;
    private static final String filterString ="CREATE";

    public GroupResponseWrapper(){}

    public GroupResponseWrapper(Group group){
        this.id =group.getUid();
        this.groupName = group.getGroupName();
    }

    public GroupResponseWrapper(Group group, Event event, Role role){

        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = event.getName();
        this.dateTime = event.getEventStartDateTime();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions(),filterString);

    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role){
        this.id = group.getUid();
        this.groupName = group.getGroupName();
        this.description = groupLog.getDescription();
        Instant instant = Instant.ofEpochMilli(groupLog.getCreatedDateTime().getTime());
        this.dateTime =new Timestamp(groupLog.getCreatedDateTime().getTime());
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
    public Timestamp getDateTime(){return dateTime;}




}
