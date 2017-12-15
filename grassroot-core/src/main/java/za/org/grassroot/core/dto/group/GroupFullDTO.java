package za.org.grassroot.core.dto.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.dto.MembershipDTO;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApiModel @Getter
public class GroupFullDTO extends GroupHeavyDTO {

    private final String joinCode;
    private final Set<MembershipDTO> members;
    @Setter private List<MembershipRecordDTO> memberHistory;

    public GroupFullDTO(Group group, Membership membership) {
        super(group, membership);
        this.joinCode = group.getGroupTokenCode();

        if (membership.getRole().getPermissions().contains(Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)) {
            this.members = group.getMemberships().stream()
                .map(MembershipDTO::new).collect(Collectors.toSet());
        } else {
            this.members = new HashSet<>();
        }
    }

}
