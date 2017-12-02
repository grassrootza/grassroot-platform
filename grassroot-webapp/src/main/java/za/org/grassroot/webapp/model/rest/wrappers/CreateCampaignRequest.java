package za.org.grassroot.webapp.model.rest.wrappers;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import org.hibernate.validator.constraints.NotBlank;
import za.org.grassroot.core.domain.campaign.CampaignType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;


@ApiModel(value = "CreateCampaignRequest")
public class CreateCampaignRequest implements Serializable {

    private static final long serialVersionUID = -3263387305104374730L;
    @ApiModelProperty(value = "campaign creater user uid", required = true)
    private String userUid;
    @ApiModelProperty(value = "name of the campaign", required = true)
    private String name;
    @ApiModelProperty(value = "campaign code", required = true)
    private String code;
    @ApiModelProperty(value = "description of campaign", required = true)
    private String description;
    @ApiModelProperty(value = "start date of campaign",required = true)
    private String startDate;
    @ApiModelProperty(value = "end date of campaign", required = true)
    private String endDate;
    @ApiModelProperty(value = "type of campaign",allowableValues = "Acquisition,Petition,Information", required = true)
    private CampaignType type;
    @ApiModelProperty(value = "url of campaign", required = true)
    private String url;
    @ApiModelProperty(value = "uid of campaign master group if group exist", required = true)
    private String groupUid;
    @ApiModelProperty(value = "name of campaign master group for new group", required = true)
    private String groupName;
    @ApiModelProperty(value = "tags for campaign", required = false)
    private Set<String> tags;

    public CreateCampaignRequest(){}

    @NotBlank(message = "campaign.user.uid.required")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotBlank(message = "campaign.name.required")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @NotBlank(message = "campaign.code.required")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @NotBlank(message = "campaign.description.required")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @NotBlank(message = "campaign.start.date.required")
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @NotBlank(message = "campaign.end.date.required")
    public String getEndDate() {
        return endDate;
    }

    public void setEndDate(String endDate) {
        this.endDate = endDate;
    }

    public Set<String> getTags() {
        return tags;
    }

    public void setTags(Set<String> tags) {
        this.tags = tags;
    }

    @NotNull(message = "campaign.type.required")
    public CampaignType getType() {
        return type;
    }

    public void setType(CampaignType type) {
        this.type = type;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public void setGroupUid(String groupUid) {
        this.groupUid = groupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public void setGroupName(String groupName) {
        this.groupName = groupName;
    }
}
