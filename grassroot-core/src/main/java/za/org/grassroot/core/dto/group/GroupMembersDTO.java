package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.membership.MembershipDTO;
import za.org.grassroot.core.repository.MembershipRepository;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class GroupMembersDTO extends GroupRefDTO {

    private final Set<MembershipDTO> members;

    public GroupMembersDTO(Group group, MembershipRepository membershipRepository) {
        super(group, membershipRepository);

        this.members = group.getMemberships().stream()
                .map(MembershipDTO::new)
                .collect(Collectors.toSet());
    }
}
