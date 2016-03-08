package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by paballo on 2016/03/01.
 */
public class GroupResponseWrapper {

    private Long id;
    private String groupName;
    private String description;
    private Timestamp timestamp;
    private String groupCreator;
    private String role;
    private Integer groupMemberCount;
    private List<String> permissions;


    public String getDescription() {
        return description;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Timestamp getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Timestamp timestamp) {
        this.timestamp = timestamp;
    }

    public String getGroupCreator() {
        return groupCreator;
    }

    public void setGroupCreator(String groupCreator) {
        this.groupCreator = groupCreator;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        GroupResponseWrapper that = (GroupResponseWrapper) o;

        if (id != null ? !id.equals(that.id) : that.id != null) return false;
        if (groupName != null ? !groupName.equals(that.groupName) : that.groupName != null) return false;
        return timestamp != null ? timestamp.equals(that.timestamp) : that.timestamp == null;

    }

    @Override
    public int hashCode() {
        int result = id != null ? id.hashCode() : 0;
        result = 31 * result + (groupName != null ? groupName.hashCode() : 0);
        result = 31 * result + (timestamp != null ? timestamp.hashCode() : 0);
        return result;
    }

    public Integer getGroupMemberCount() {
        return groupMemberCount;
    }

    public void setGroupMemberCount(Integer groupMemberCount) {
        this.groupMemberCount = groupMemberCount;
    }

    public List<String> getPermissions() {
        return permissions;
    }

    public void setPermissions(List<String> permissions) {
        this.permissions = permissions;
    }
}
