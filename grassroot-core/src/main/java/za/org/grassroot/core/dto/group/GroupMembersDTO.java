package za.org.grassroot.core.dto.group;

import lombok.Getter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.dto.MembershipDTO;

import java.util.Set;
import java.util.stream.Collectors;

@Getter
public class GroupMembersDTO extends GroupRefDTO {

    private final Set<MembershipDTO> members;

    public GroupMembersDTO(Group group) {
        super(group.getUid(), group.getName(), 0);

        this.members = group.getMemberships().stream()
                .map(MembershipDTO::new).collect(Collectors.toSet());
        this.memberCount = this.members.size();
    }
}
