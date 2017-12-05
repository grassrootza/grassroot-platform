package za.org.grassroot.core.dto.group;

import lombok.Getter;

@Getter
public class GroupRefDTO {

    private String groupUid;
    private String name;

    public GroupRefDTO(String groupUid, String groupName) {
        this.groupUid = groupUid;
        this.name = groupName;
    }
}
