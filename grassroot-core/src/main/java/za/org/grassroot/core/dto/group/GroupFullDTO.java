package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.dto.MembershipDTO;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class GroupFullDTO extends GroupHeavyDTO {

    private final String joinCode;
    private final Set<MembershipDTO> members;

    public GroupFullDTO(Group group, Membership membership) {
        super(group, membership);
        this.joinCode = group.getGroupTokenCode();
        this.memberCount = (long) group.getMemberships().size();
        this.members = group.getMemberships().stream()
                .map(MembershipDTO::new).collect(Collectors.toSet());
    }

}
