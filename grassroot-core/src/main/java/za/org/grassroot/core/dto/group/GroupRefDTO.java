package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.repository.MembershipRepository;

@Getter
public class GroupRefDTO {

    protected String groupUid;
    protected String name;
    protected int memberCount;

    public GroupRefDTO(Group group, MembershipRepository membershipRepository) {
        this.groupUid = group.getUid();
        this.name = group.getName();
        this.memberCount = membershipRepository.countByGroup(group);
    }
}
