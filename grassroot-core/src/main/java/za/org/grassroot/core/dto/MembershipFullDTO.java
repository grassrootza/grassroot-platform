package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;

import java.util.List;

@Getter
public class MembershipFullDTO {

    private final UserFullDTO user;
    private final GroupMinimalDTO group;
    private final String roleName;
    private final List<String> topics;

    public MembershipFullDTO(Membership membership) {
        this.user = new UserFullDTO(membership.getUser());
        this.group = new GroupMinimalDTO(membership.getGroup(), membership);
        this.roleName = membership.getRole().getName();
        this.topics = membership.getTopics();
    }
}
