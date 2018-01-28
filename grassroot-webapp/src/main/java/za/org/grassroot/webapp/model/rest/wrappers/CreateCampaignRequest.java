package za.org.grassroot.webapp.model.rest.wrappers;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;
import lombok.Setter;
import org.hibernate.validator.constraints.NotBlank;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.campaign.CampaignType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;


@ApiModel(value = "CreateCampaignRequest") @Setter
public class CreateCampaignRequest implements Serializable {

    private static final long serialVersionUID = -3263387305104374730L;

    @ApiModelProperty(value = "name of the campaign", required = true)
    private String name;
    @ApiModelProperty(value = "campaign code", required = true)
    private String code;
    @ApiModelProperty(value = "description of campaign", required = true)
    private String description;
    @ApiModelProperty(value = "start date of campaign, in epoch millis",required = true)
    private long startDateEpochMillis;
    @ApiModelProperty(value = "end date of campaign, in epoch millis", required = true)
    private long endDateEpochMillis;
    @ApiModelProperty(value = "type of campaign",allowableValues = "ACQUISITION,PETITION,INFORMATION", required = true)
    private CampaignType type;
    @ApiModelProperty(value = "url of campaign", required = true)
    private String url;
    @ApiModelProperty(value = "uid of campaign master group if group exist")
    private String groupUid;
    @ApiModelProperty(value = "name of campaign master group for new group")
    private String groupName;
    @ApiModelProperty(value = "tags for campaign", required = false)
    private Set<String> tags;

    public CreateCampaignRequest(){}

    @NotBlank(message = "campaign.name.required")
    public String getName() {
        return name;
    }

    @NotBlank(message = "campaign.code.required")
    public String getCode() {
        return code;
    }

    @NotBlank(message = "campaign.description.required")
    public String getDescription() {
        return description;
    }

    public long getStartDateEpochMillis() {
        return startDateEpochMillis;
    }
    public long getEndDateEpochMillis() {
        return endDateEpochMillis;
    }

    public Set<String> getTags() {
        return tags;
    }

    @NotNull(message = "campaign.type.required")
    public CampaignType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    public String getGroupUid() {
        return groupUid;
    }

    public String getGroupName() {
        return groupName;
    }

    public boolean hasNoGroup() {
        return StringUtils.isEmpty(groupName) && StringUtils.isEmpty(groupUid);
    }

}
