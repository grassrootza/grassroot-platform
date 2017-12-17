package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;

@Getter
public class MembershipFullDTO {

    private final UserFullDTO user;
    private final GroupMinimalDTO group;
    private final String roleName;

    public MembershipFullDTO(Membership membership) {
        this.user = new UserFullDTO(membership.getUser());
        this.group = new GroupMinimalDTO(membership.getGroup(), membership);
        this.roleName = membership.getRole().getName();
    }
}
