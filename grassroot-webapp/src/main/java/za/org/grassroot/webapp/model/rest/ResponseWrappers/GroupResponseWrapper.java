package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import org.springframework.http.MediaType;
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
    private LocalDateTime dateTime;

    private boolean hasTasks;

    private GroupResponseWrapper(Group group, Role role, boolean hasTasks) {
        this.groupUid = group.getUid();
        this.groupName = group.getName("");
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.groupMemberCount = group.getMemberships().size();
        this.role = (role!=null)?role.getName():null;
        this.permissions = RestUtil.filterPermissions(role.getPermissions());
        this.hasTasks = hasTasks;
        this.imageUrl =generateImageUrl(group);

        if (group.hasValidGroupTokenCode()) {
            this.joinCode = group.getGroupTokenCode();
        } else {
            this.joinCode = "NONE";
        }
    }

    public GroupResponseWrapper(Group group, Event event, Role role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(event);
        this.description = event.getName();
        this.dateTime = event.getEventDateTimeAtSAST();
        this.imageUrl =generateImageUrl(group);
    }

    public GroupResponseWrapper(Group group, GroupLog groupLog, Role role, boolean hasTasks){
        this(group, role, hasTasks);
        this.lastChangeType = GroupChangeType.getChangeType(groupLog);
        this.description = (groupLog.getDescription()!=null) ? groupLog.getDescription() : group.getDescription();
        this.dateTime = groupLog.getCreatedDateTime().atZone(DateTimeUtil.getSAST()).toLocalDateTime();
        this.imageUrl = generateImageUrl(group);
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

    public boolean isHasTasks() { return hasTasks; }


    private static String generateImageUrl(Group group){
        String imageUrl = group.getUid();
        if(group.getImage() != null){
            switch(group.getImageType()){
                case MediaType.IMAGE_JPEG_VALUE:
                    imageUrl = imageUrl + ".jpg";
                    break;
                case MediaType.IMAGE_GIF_VALUE:
                    imageUrl = imageUrl +".gif";
                    break;
                case MediaType.IMAGE_PNG_VALUE:
                    imageUrl = imageUrl +".png";
                    break;
                default:
                    imageUrl = null;
                    break;
            }
        }
        return imageUrl;
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
                return Role.compareRoleNames(this.getRole(), g.getRole());
            }
        }

    }

    @Override
    public String toString() {
        return "GroupResponseWrapper{" +
                "groupUid='" + groupUid + '\'' +
                ", lastChangeType=" + lastChangeType +
                ", groupName='" + groupName + '\'' +
                '}';
    }
}
