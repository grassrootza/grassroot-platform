package za.org.grassroot.webapp.model.rest.wrappers;

import com.fasterxml.jackson.annotation.JsonInclude;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.group.Group;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/03/16.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupSearchWrapper implements Comparable<GroupSearchWrapper> {

    protected static final DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE, d MMM");

    private String id;
    private String groupName;
    private String description;
    private String groupCreator;
    private Integer memberCount;

    private boolean termInName;
    private boolean hasOpenRequest;
    private boolean hasLocationData;

    private String createdDate;

    public GroupSearchWrapper(Group group) {
        this.id =group.getUid();
        this.groupName = group.getGroupName();
        this.description = group.getDescription();
        this.groupCreator = group.getCreatedByUser().getDisplayName();
        this.memberCount = group.getMemberships().size();
        this.hasOpenRequest = false;
        this.createdDate = group.getCreatedDateTimeAtSAST().format(dateFormat);
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

    public Integer getMemberCount() {
        return memberCount;
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

    public String getCreatedDate() {
        return createdDate;
    }

    public void setCreatedDate(String createdDate) {
        this.createdDate = createdDate;
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
