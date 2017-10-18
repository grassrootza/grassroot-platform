package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;

import java.util.Set;

@Getter
public class GroupHeavyDTO extends GroupMinimalDTO {

    private final String groupCreatorName;
    private final String groupCreatorUid;

    public GroupHeavyDTO(Group group, Membership membership) {
        super(group, membership.getRole());
        this.groupCreatorUid = group.getCreatedByUser().getUid();
        this.groupCreatorName = group.getCreatedByUser().getName();
    }

}
