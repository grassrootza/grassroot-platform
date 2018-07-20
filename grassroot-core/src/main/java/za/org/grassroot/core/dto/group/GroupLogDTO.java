package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.dto.UserDTO;

import java.time.Instant;

@Getter
public class GroupLogDTO {

    private Long id;
    private Instant createdDateTime;
    private UserDTO targetUser;
    private String description;


    public GroupLogDTO(GroupLog groupLog) {
        this.id = groupLog.getId();
        this.createdDateTime = groupLog.getCreatedDateTime();
        this.targetUser = new UserDTO(groupLog.getTargetUser());
        this.description = groupLog.getDescription();
    }
}
