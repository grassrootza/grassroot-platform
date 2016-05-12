package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.util.Set;


/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper implements Comparable<GroupResponseWrapper> {

    private String id;
    private String groupName;
    private String description = "Group has no event.";
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;
    private LocalDateTime dateTime;
    private Set<Permission> permissions;

    public GroupResponseWrapper(Group group, Event event, Role role){
        this.id = group.getUid();
        this.groupName = group.getName("");
        this.description = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions());
    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role){
        this.id = group.getUid();
        this.groupName = group.getName("");
        this.description = groupLog.getDescription();
        this.dateTime = groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.role = (role!=null)?role.getName():null;
        this.groupMemberCount = group.getMemberships().size();
        this.permissions = RestUtil.filterPermissions(role.getPermissions());
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

    @Override
    public int compareTo(GroupResponseWrapper g) {

        String otherGroupUid = g.getId();

        if (id == null) throw new UnsupportedOperationException("Error! Comparing group wrappers with null IDs");

        if (id.equals(otherGroupUid)) {
            return 0;
        } else {
            LocalDateTime otherDateTime = g.getDateTime();
            if (dateTime.compareTo(otherDateTime) != 0) {
                return dateTime.compareTo(otherDateTime);
            } else {
                // this is very low probability, as date time is to the second, but ...
                return Role.compareRoleNames(this.getRole(), g.getRole());
            }
        }

    }



}
