package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.repository.MembershipRepository;

@Getter
public class PublicGroupDTO extends GroupRefDTO {

    public final String groupCreatorName;
    public final Long groupCreationTimeMillis;

    public PublicGroupDTO(Group group, MembershipRepository membershipRepository) {
        super(group, membershipRepository);
        this.groupCreatorName = group.getCreatedByUser().getName();
        this.groupCreationTimeMillis = group.getCreatedDateTime().toEpochMilli();
    }
}
