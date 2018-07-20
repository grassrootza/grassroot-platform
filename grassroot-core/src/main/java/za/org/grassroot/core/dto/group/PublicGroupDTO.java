package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;

@Getter
public class PublicGroupDTO extends GroupRefDTO {

    public final String groupCreatorName;
    public final Long groupCreationTimeMillis;

    public PublicGroupDTO(Group group) {
        super(group.getUid(), group.getName(), group.getMembers().size());
        this.groupCreatorName = group.getCreatedByUser().getName();
        this.groupCreationTimeMillis = group.getCreatedDateTime().toEpochMilli();
    }
}
