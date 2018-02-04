package za.org.grassroot.webapp.controller.rest.group;

import io.swagger.annotations.ApiModel;
import lombok.Getter;
import za.org.grassroot.core.dto.MembershipInfo;

import java.util.List;

@ApiModel @Getter
public class GroupModifiedResponse {

    private String groupName;
    private Integer membersAdded;
    private List<MembershipInfo> invalidMembers;

    public GroupModifiedResponse(String groupName, Integer membersAdded, List<MembershipInfo> invalidMembers) {
        this.groupName = groupName;
        this.membersAdded = membersAdded;
        this.invalidMembers = invalidMembers;
    }
}
