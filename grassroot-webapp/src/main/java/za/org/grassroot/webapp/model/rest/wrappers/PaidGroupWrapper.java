package za.org.grassroot.webapp.model.rest.wrappers;

import za.org.grassroot.core.domain.group.Group;

/**
 * Created by luke on 2017/01/25.
 */
public class PaidGroupWrapper {

    private final String groupUid;
    private final String groupName;
    private final String description;
    private final String groupCreator;
    private final Integer groupMemberCount;

    public PaidGroupWrapper(Group group) {
        this.groupUid = group.getUid();
        this.groupName = group.getName();
        this.description = group.getDescription();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.groupMemberCount = group.getMemberships().size();
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public String getDescription() {
        return description;
    }

    public String getGroupCreator() {
        return groupCreator;
    }

    public Integer getGroupMemberCount() {
        return groupMemberCount;
    }
}
