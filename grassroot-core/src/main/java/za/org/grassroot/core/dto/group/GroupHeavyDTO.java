package za.org.grassroot.core.dto.group;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.Permission;

import java.util.Set;

public class GroupHeavyDTO extends GroupMinimalDTO {

    private final String groupCreatorName;
    private final String groupCreatorUid;
    private final Set<Permission> userPermissions;

    public GroupHeavyDTO(Group group, Membership membership) {
        super(group, membership.getRole());
        this.groupCreatorUid = group.getCreatedByUser().getUid();
        this.groupCreatorName = group.getCreatedByUser().getName();
        this.userPermissions = membership.getRole().getPermissions();
    }

    public Set<Permission> getUserPermissions() {
        return userPermissions;
    }

    public String getGroupCreatorName() {
        return groupCreatorName;
    }

    public String getGroupCreatorUid() {
        return groupCreatorUid;
    }
}
