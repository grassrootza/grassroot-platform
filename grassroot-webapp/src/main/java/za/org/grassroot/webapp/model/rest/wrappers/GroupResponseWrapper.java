package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.GroupDefaultImage;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.controller.android1.LegacyDateTimeSerializer;
import za.org.grassroot.webapp.enums.GroupChangeType;
import za.org.grassroot.webapp.util.RestUtil;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


/**
 * Created by paballo on 2016/03/01.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupResponseWrapper implements Comparable<GroupResponseWrapper> {

    private static final int MAX_NUMBER_MEMBERS_SEND = 300;

    private final String groupUid;
    private final String groupName;
    private final String groupCreator;
    private final GroupRole role;
    private final String joinCode;
    private final Integer groupMemberCount;
    private Long lastMajorChangeMillis; // i.e., time last event was created, and/or group modified

    private final Set<Permission> permissions;

    private String description;
    private GroupChangeType lastChangeType;
    private String lastChangeDescription;

    @JsonSerialize(using = LegacyDateTimeSerializer.class)
    private LocalDateTime dateTime;

    private String imageUrl;
    private GroupDefaultImage defaultImage;

    private boolean hasTasks;
    private boolean discoverable;
    private boolean paidFor;

    private List<MembershipResponseWrapper> members;
    private List<String> invalidNumbers; // for added member / group creation error handling

    private String language;

    private GroupResponseWrapper(Group group, GroupRole role, boolean hasTasks) {
        Objects.requireNonNull(group);
        Objects.requireNonNull(role);

        this.groupUid = group.getUid();
        this.groupName = group.getName("");
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.groupMemberCount = group.getMemberships().size();
        this.role = role;
        this.permissions = RestUtil.filterPermissions(group.getPermissions(role));
        this.hasTasks = hasTasks;
        this.discoverable = group.isDiscoverable();
        this.imageUrl = group.getImageUrl();
        this.defaultImage = group.getDefaultImage();
        this.description = group.getDescription();
        this.paidFor = group.isPaidFor();
        this.language = group.getDefaultLanguage();

        if (group.hasValidGroupTokenCode()) {
            this.joinCode = group.getGroupTokenCode();
        } else {
            this.joinCode = "NONE";
        }

        if (permissions.contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            this.members = group.getMemberships()
                    .stream().limit(MAX_NUMBER_MEMBERS_SEND)
                    .map(membership -> new MembershipResponseWrapper(group, membership.getUser(), membership.getRole(), false))
                    .collect(Collectors.toList());
        }

        this.invalidNumbers = new ArrayList<>();
    }

    public GroupResponseWrapper(Group group, Event event, GroupRole role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(event);
        this.lastChangeDescription = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
        this.lastMajorChangeMillis = group.getLatestChangeOrTaskTime().toEpochMilli();
    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, GroupRole role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(groupLog);
        this.lastChangeDescription = (groupLog != null && groupLog.getDescription()!=null) ?
                groupLog.getDescription() : group.getDescription();
        this.dateTime = groupLog == null ? group.getCreatedDateTimeAtSAST().toLocalDateTime() :
                groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
        this.lastMajorChangeMillis = groupLog == null ? group.getCreatedDateTime().toEpochMilli() :
                groupLog.getCreatedDateTime().toEpochMilli();
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

    public GroupRole getRole() {
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

    public String getLastChangeDescription() { return lastChangeDescription; }

    public boolean isHasTasks() { return hasTasks; }

    public boolean isDiscoverable() { return discoverable; }

    public String getImageUrl() {
        return imageUrl;
    }

    public GroupDefaultImage getDefaultImage() { return defaultImage; }

    public List<MembershipResponseWrapper> getMembers() { return members; }

    public List<String> getInvalidNumbers() {
        return invalidNumbers;
    }

    public void setInvalidNumbers(List<String> invalidNumbers) {
        this.invalidNumbers = invalidNumbers;
    }

    public boolean isPaidFor() { return paidFor; }

    public String getLanguage() {
        return language;
    }

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
                return this.getRole().compareTo(g.getRole());
            }
        }

    }

    public String printMembers() {
        StringBuilder sb = new StringBuilder("members : { ");
        for (MembershipResponseWrapper m : members) {
            sb.append("(name = ");
            sb.append(m.getDisplayName());
            sb.append(", number = ");
            sb.append(m.getPhoneNumber());
            sb.append("), ");
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public String toString() {
        return "GroupResponseWrapper{" +
                "groupUid='" + groupUid + '\'' +
                ", lastChangeType=" + lastChangeType +
                ", lastMajorChangeMillis=" + lastMajorChangeMillis +
                ", groupName='" + groupName + '\'' +
                ", members='" + ((members == null) ? "none" : members.size()) + '\'' +
                ", invalidNumbers='" + (invalidNumbers != null ? invalidNumbers.toString() : "none") + '\'' +
                '}';
    }
}
