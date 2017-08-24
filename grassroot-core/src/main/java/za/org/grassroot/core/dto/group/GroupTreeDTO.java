package za.org.grassroot.core.dto.group;

/**
 * Created by aakilomar on 11/28/15.
 */
public class GroupTreeDTO {

    private Long groupId;
    private String groupName;
    private Long parentId;
    private Long root;

    public GroupTreeDTO(Object[] objArray) {
        groupId = Long.parseLong(objArray[0].toString());
        groupName = objArray[1].toString();
        if (objArray[2] != null) {
            parentId = Long.parseLong(objArray[2].toString());
        }
        root = Long.parseLong(objArray[3].toString());
    }


    public Long getGroupId() {
        return groupId;
    }

    public void setGroupId(Long groupId) {
        this.groupId = groupId;
    }

    public String getGroupName() {
        if (groupName == null || groupName.trim().equals("")) {
            groupName = "Group " + groupId;
        }
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public Long getRoot() {
        return root;
    }

    public void setRoot(Long root) {
        this.root = root;
    }

    @Override
    public String toString() {
        return "GroupTreeDTO{" +
                "groupId=" + groupId +
                ", groupName='" + groupName + '\'' +
                ", parentId=" + parentId +
                ", root=" + root +
                '}';
    }
}
