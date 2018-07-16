package za.org.grassroot.core.dto.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;

@ApiModel
@Getter
public class GroupAdminDTO extends GroupRefDTO{
    protected String creatingUserName;
    protected boolean active;

    public GroupAdminDTO(Group group){
        super(group.getUid(),group.getName(),group.getMembers().size());
        this.creatingUserName = group.getCreatedByUser().getDisplayName();
        this.active = group.isActive();
    }
}
