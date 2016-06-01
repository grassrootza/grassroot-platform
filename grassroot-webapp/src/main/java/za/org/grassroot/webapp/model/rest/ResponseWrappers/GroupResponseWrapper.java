package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.enums.GroupChangeType;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.util.Set;


/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper implements Comparable<GroupResponseWrapper> {

    private String groupUid;
    private String groupName;
    private String description;
    private GroupChangeType lastChangeType;
    private String groupCreator;

    private String role;
    private String joinCode;
    private Integer groupMemberCount;
    private LocalDateTime dateTime;
    private Set<Permission> permissions;

    private GroupResponseWrapper(Group group, Role role) {
        this.groupUid = group.getUid();
        this.groupName = group.getName("");
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.groupMemberCount = group.getMemberships().size();
        this.role = (role!=null)?role.getName():null;
        this.permissions = RestUtil.filterPermissions(role.getPermissions());

        if (group.hasValidGroupTokenCode()) {
            this.joinCode = group.getGroupTokenCode();
        } else {
            this.joinCode = "NONE";
        }
    }

    public GroupResponseWrapper(Group group, Event event, Role role){
        this(group, role);
        this.lastChangeType = GroupChangeType.getChangeType(event);
        this.description = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role){
        this(group, role);
        this.lastChangeType = GroupChangeType.getChangeType(groupLog);
        this.description = (groupLog.getDescription()!=null) ? groupLog.getDescription() : group.getDescription();
        this.dateTime = groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
    }

    public String getGroupUid() {
        return groupUid;
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

    public String getJoinCode() { return joinCode; }

    public GroupChangeType getLastChangeType() { return lastChangeType; }

    @Override
    public int compareTo(GroupResponseWrapper g) {

        String otherGroupUid = g.getGroupUid();

        if (groupUid == null) throw new UnsupportedOperationException("Error! Comparing group wrappers with null IDs");

        if (groupUid.equals(otherGroupUid)) {
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
