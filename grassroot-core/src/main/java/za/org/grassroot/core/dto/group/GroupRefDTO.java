package za.org.grassroot.core.dto.group;

import lombok.Getter;

@Getter
public class GroupRefDTO {

    protected String groupUid;
    protected String name;
    protected int memberCount;

    public GroupRefDTO(String groupUid, String groupName, int memberCount) {
        this.groupUid = groupUid;
        this.name = groupName;
        this.memberCount = memberCount;
    }
}
