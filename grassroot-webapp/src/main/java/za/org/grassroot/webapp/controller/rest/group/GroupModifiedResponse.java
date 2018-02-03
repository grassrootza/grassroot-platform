package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;
import za.org.grassroot.core.dto.MembershipInfo;

import java.util.List;

@ApiModel
@Getter
public class GroupModifiedResponse {

    private Integer membersAdded;
    private List<MembershipInfo> invalidMembers;

    public GroupModifiedResponse(Integer membersAdded, List<MembershipInfo> invalidMembers) {
        this.membersAdded = membersAdded;
        this.invalidMembers = invalidMembers;
    }
}
