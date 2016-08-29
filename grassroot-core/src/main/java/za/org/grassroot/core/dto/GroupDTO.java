package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.util.DateTimeUtil;

import java.time.Instant;
import java.time.ZonedDateTime;

/**
 * Created by paballo on 2016/02/15.
 */
public class GroupDTO {

    private Long id;
    private String uid;
    private boolean active;
    private Instant created_date_time;
    private String description;
    private String groupName;
    private int group_size;

    public GroupDTO(Group group){
        this.id = group.getId();
        this.uid = group.getUid();
        this.active = group.isActive();
        this.created_date_time = group.getCreatedDateTime();
        this.groupName = group.getGroupName();
        this.group_size = group.getMembers().size();
        this.description = group.getDescription();
        
    }

    private GroupDTO(){

    }

    public GroupDTO(Object[] objArray) {
        id = Long.parseLong(objArray[0].toString());
        uid = objArray[1].toString();
        active = (Boolean) objArray[4];
        created_date_time = (Instant) objArray[2];
        groupName = objArray[3].toString();
        group_size = (Integer) objArray[5];
    }

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedDateTime() {
        return created_date_time;
    }

    public ZonedDateTime getCreatedDateTimeAtSAST() {
        return DateTimeUtil.convertToUserTimeZone(created_date_time, DateTimeUtil.getSAST());
    }

    public String getGroupName() {
        return groupName;
    }

    public String getUid(){return uid;}

    public int getGroupSize() {
        return group_size;
    }

    public Long getId() {return id;}

    public boolean hasName() {
        return (groupName != null && groupName.trim().length() != 0);
    }

    public String getDisplayName(String unnamedPrefix) {
        if (hasName()) {
            return groupName;
        } else if (unnamedPrefix.trim().length() == 0 && group_size != 0) {
            return "Unnamed group (" + group_size + " members)";
        } else {
            return unnamedPrefix;
        }
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return groupName;
    }
}
