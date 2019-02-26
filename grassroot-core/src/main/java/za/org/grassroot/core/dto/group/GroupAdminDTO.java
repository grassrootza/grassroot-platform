package za.org.grassroot.core.dto.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.repository.MembershipRepository;

@ApiModel
@Getter
public class GroupAdminDTO extends GroupRefDTO {
    protected String creatingUserName;
    protected boolean active;

    public GroupAdminDTO(Group group, MembershipRepository membershipRepository){
        super(group, membershipRepository);
        this.creatingUserName = group.getCreatedByUser().getDisplayName();
        this.active = group.isActive();
    }
}
