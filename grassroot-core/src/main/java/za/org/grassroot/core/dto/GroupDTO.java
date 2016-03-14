package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Group;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by paballo on 2016/02/15.
 */
public class GroupDTO {

    private Long id;
    private boolean active;
    private Timestamp created_date_time;
    private String description;
    private String groupName;
    private int group_size;


    private List<String> phoneNumbers;

    public GroupDTO(Group group){
    }

    private GroupDTO(){

    }


    public GroupDTO(Object[] objArray) {
        id = Long.parseLong(objArray[0].toString());
        created_date_time = (Timestamp) objArray[1];
        groupName = objArray[2].toString();
        active = (Boolean) objArray[3];
        group_size = (Integer) objArray[4];
    }

    public boolean isActive() {
        return active;
    }

    public Timestamp getCreatedDateTime() {
        return created_date_time;
    }

    public String getGroupName() {
        return groupName;
    }

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
        } else if (unnamedPrefix.trim().length() == 0) {
            return "Unnamed group (" + group_size + " members)";
        } else {
            return unnamedPrefix;
        }
    }

    public List<String> getPhoneNumbers() {
        return phoneNumbers;
    }

    public String getDescription() {
        return description;
    }

    @Override
    public String toString() {
        return phoneNumbers.toString();
    }

}
