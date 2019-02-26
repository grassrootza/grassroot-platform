package za.org.grassroot.core.dto.membership;

import lombok.Getter;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.dto.UserFullDTO;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.core.repository.MembershipRepository;

import java.util.List;
import java.util.function.Function;

@Getter
public class MembershipFullDTO {

    private final String displayName;
    private final UserFullDTO user;
    private final GroupMinimalDTO group;
    private final GroupRole roleName;
    private final List<String> topics;
    private final GroupJoinMethod joinMethod;
    private final String joinMethodDescriptor;
    private final List<String> affiliations;
    private final boolean canEditDetails;

    public MembershipFullDTO(Membership membership, MembershipRepository membershipRepository) {
        this.displayName = membership.getDisplayName();
        this.user = new UserFullDTO(membership.getUser());
        this.group = new GroupMinimalDTO(membership.getGroup(), membership, membershipRepository);
        this.roleName = membership.getRole();
        this.topics = membership.getTopics();
        this.joinMethod = membership.getJoinMethod();
        this.joinMethodDescriptor = membership.getJoinMethodDescriptor().orElse("");
        this.affiliations = membership.getAffiliations();
        this.canEditDetails = !(membership.getUser().hasPassword() || membership.getUser().isHasSetOwnName());
    }
}
