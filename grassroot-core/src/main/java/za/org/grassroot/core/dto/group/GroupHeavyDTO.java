package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;

@Getter
public class GroupHeavyDTO extends GroupMinimalDTO {

    private final String groupCreatorName;
    private final String groupCreatorUid;
    protected final Long groupCreationTimeMillis;

    public GroupHeavyDTO(Group group, Membership membership) {
        super(group, membership);
        this.groupCreatorUid = group.getCreatedByUser().getUid();
        this.groupCreatorName = group.getCreatedByUser().getName();
        this.groupCreationTimeMillis = group.getCreatedDateTime().toEpochMilli();
    }

}
