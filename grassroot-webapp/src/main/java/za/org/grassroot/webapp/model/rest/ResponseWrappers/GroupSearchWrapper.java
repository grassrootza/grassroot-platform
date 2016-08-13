package za.org.grassroot.webapp.model.rest.ResponseWrappers;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinRequest;

import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/03/16.
 */
public class GroupSearchWrapper implements Comparable<GroupSearchWrapper> {
    private String id;
    private String groupName;
    private String description;
    private String groupCreator;
    private Integer count;
    private boolean termInName;
    private boolean hasOpenRequest;
    private boolean hasLocationData;

    public GroupSearchWrapper(Group group) {
        this.id =group.getUid();
        this.groupName = group.getGroupName();
        this.description = group.getDescription() == null ? "Group has no public description" : group.getDescription();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.count = group.getMemberships().size();
        this.hasOpenRequest = false;
    }

    public GroupSearchWrapper(Group group, boolean termInName, boolean hasLocationData, List<GroupJoinRequest> openRequestsForPossibleGroups) {
        this(group);
        this.termInName = termInName;
        openRequestsForPossibleGroups.forEach(request ->
                this.hasOpenRequest = request.getGroup().equals(group));
    }

    public String getId() {
        return id;
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

    public Integer getCount() {
        return count;
    }

    public boolean isTermInName() {
        return termInName;
    }

    public boolean isHasOpenRequest() {
        return hasOpenRequest;
    }

    public boolean isHasLocationData() {
        return hasLocationData;
    }

    @Override
    public int compareTo(GroupSearchWrapper g) {
        Objects.requireNonNull(g);
        return (this.hasOpenRequest && !g.hasOpenRequest) ? 1
                : (!this.hasOpenRequest && g.hasOpenRequest) ? -1
                : (this.termInName && !g.termInName) ? 1
                : (!this.termInName && g.termInName) ? -1
                : this.groupName.compareTo(g.groupName);
    }
}
