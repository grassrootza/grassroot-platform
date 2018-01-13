package za.org.grassroot.core.dto;

import lombok.Getter;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;

import java.util.List;
import java.util.Optional;

@Getter
public class MembershipFullDTO {

    private final UserFullDTO user;
    private final GroupMinimalDTO group;
    private final String roleName;
    private final List<String> topics;
    private final GroupJoinMethod joinMethod;
    private final String joinMethodDescriptor;

    public MembershipFullDTO(Membership membership) {
        this.user = new UserFullDTO(membership.getUser());
        this.group = new GroupMinimalDTO(membership.getGroup(), membership);
        this.roleName = membership.getRole().getName();
        this.topics = membership.getTopics();
        this.joinMethod = membership.getJoinMethod();
        this.joinMethodDescriptor = membership.getTagList().stream()
                .filter(s -> s.startsWith(Membership.JOIN_METHOD_DESCRIPTOR_TAG)).findFirst().orElse(null);
    }
}
