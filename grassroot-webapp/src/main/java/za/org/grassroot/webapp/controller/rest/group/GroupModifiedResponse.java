package za.org.grassroot.webapp.controller.rest.group;

import com.fasterxml.jackson.annotation.JsonIgnore;
import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Getter;

import java.util.List;

@ApiModel
@Getter
public class GroupModifiedResponse {

    private Integer membersAdded;
    private List<String> invalidNumbers;

    public GroupModifiedResponse(Integer membersAdded, List<String> invalidNumbers) {
        this.membersAdded = membersAdded;
        this.invalidNumbers = invalidNumbers;
    }
}
