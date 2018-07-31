package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;

@Getter @JsonInclude(JsonInclude.Include.NON_NULL)
public class MemberFrontPageInfo {

    private final String displayName;
    private final String groupName;
    private final GroupJoinMethod joinMethod;
    private final String joinMethodDescriptor;

    public MemberFrontPageInfo(Membership membership) {
        this.displayName = membership.getDisplayName();
        this.groupName = membership.getGroup().getName();
        this.joinMethod = membership.getJoinMethod();
        this.joinMethodDescriptor = membership.getJoinMethodDescriptor().orElse(null);
    }

}
