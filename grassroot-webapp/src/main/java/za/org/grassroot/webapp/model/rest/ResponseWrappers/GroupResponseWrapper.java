package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.enums.GroupChangeType;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper implements Comparable<GroupResponseWrapper> {

    private final String groupUid;
    private final String groupName;
    private final String groupCreator;
    private final String role;
    private final String joinCode;
    private final Integer groupMemberCount;
    private final Set<Permission> permissions;
    private String description;
    private String imageUrl;
    private GroupChangeType lastChangeType;
    private Long lastMajorChangeMillis; // i.e., time last event was created, and/or group modified
    private LocalDateTime dateTime;

    private boolean hasTasks;
    private boolean discoverable;

    private List<MembershipResponseWrapper> members;

    private GroupResponseWrapper(Group group, Role role, boolean hasTasks) {
        this.groupUid = group.getUid();
        this.groupName = group.getName("");
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.groupMemberCount = group.getMemberships().size();
        this.role = (role!=null)?role.getName():null;
        this.permissions = RestUtil.filterPermissions(role.getPermissions());
        this.hasTasks = hasTasks;
        this.discoverable = group.isDiscoverable();
        this.imageUrl = group.getImageUrl();

        if (group.hasValidGroupTokenCode()) {
            this.joinCode = group.getGroupTokenCode();
        } else {
            this.joinCode = "NONE";
        }

        this.members = group.getMemberships().stream()
                .map(membership -> new MembershipResponseWrapper(group, membership.getUser(), membership.getRole(), false))
                .collect(Collectors.toList());
    }

    public GroupResponseWrapper(Group group, Event event, Role role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(event);
        this.description = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
        this.lastMajorChangeMillis = event.getCreatedDateTime().toEpochMilli();
    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(groupLog);
        this.description = (groupLog.getDescription()!=null) ? groupLog.getDescription() : group.getDescription();
        this.dateTime = groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
        this.lastMajorChangeMillis = groupLog.getCreatedDateTime().toEpochMilli();
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

    public Long getLastMajorChangeMillis() { return lastMajorChangeMillis; }

    public String getJoinCode() { return joinCode; }

    public GroupChangeType getLastChangeType() { return lastChangeType; }

    public boolean isHasTasks() { return hasTasks; }

    public boolean isDiscoverable() { return discoverable; }

    public String getImageUrl() {
        return imageUrl;
    }

    public List<MembershipResponseWrapper> getMembers() { return members; }

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

    @Override
    public String toString() {
        return "GroupResponseWrapper{" +
                "groupUid='" + groupUid + '\'' +
                ", lastChangeType=" + lastChangeType +
                ", lastMajorChangeMillis=" + lastMajorChangeMillis +
                ", groupName='" + groupName + '\'' +
                ", members='" + members.size() + '\'' +
                '}';
    }
}
