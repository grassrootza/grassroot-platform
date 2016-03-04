package za.org.grassroot.core.dto;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.MaskingUtil;

import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Set;

/**
 * Created by luke on 2016/02/04.
 * Entity to pass a group back to services and view with key data obscured
 */
public class MaskedGroupDTO {

    private Long id;
    private Timestamp createdDateTime;
    private User createdByUser;
    private String groupName;

    private Set<MaskedUserDTO> groupMembers = new HashSet<>();
    private Long parentId;
    private boolean paidFor;

    private boolean active;
    private boolean discoverable;

    public MaskedGroupDTO(Group group) {
        this.id = group.getId();
        this.createdDateTime = group.getCreatedDateTime();
        this.createdByUser = MaskingUtil.maskUser(group.getCreatedByUser());
        this.groupName = MaskingUtil.maskName(group.getGroupName());

        this.parentId = (group.getParent() != null) ? group.getParent().getId() : null;
        this.paidFor = group.isPaidFor();
        this.active = group.isActive();
        this.discoverable = group.isActive();

        for (Membership membership : group.getMemberships()) {
            this.groupMembers.add(new MaskedUserDTO(membership.getUser()));
        }
    }

    public Long getId() {
        return id;
    }

    public Timestamp getCreatedDateTime() {
        return createdDateTime;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public String getGroupName() {
        return groupName;
    }

    public Set<MaskedUserDTO> getGroupMembers() {
        return groupMembers;
    }

    public Long getParentId() {
        return parentId;
    }

    public boolean isPaidFor() {
        return paidFor;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isDiscoverable() {
        return discoverable;
    }
}
