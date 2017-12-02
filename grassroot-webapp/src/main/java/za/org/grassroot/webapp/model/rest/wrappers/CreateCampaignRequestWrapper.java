package za.org.grassroot.webapp.model.rest.wrappers;

import org.hibernate.validator.constraints.NotBlank;
import za.org.grassroot.core.domain.campaign.CampaignType;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Set;


public class CreateCampaignRequestWrapper implements Serializable {

    private static final long serialVersionUID = -3263387305104374730L;
    private String createUser;
    private String userUid;
    private String name;
    private String code;
    private String description;
    private String startDate;
    private String endDate;
    private CampaignType type;
    private String url;
    private String groupUid;
    private String groupName;
    private Set<String> tags;

    public CreateCampaignRequestWrapper(){}

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

    public String getCreateUser() {
        return createUser;
    }

    public void setCreateUser(String createUser) {
        this.createUser = createUser;
    }
}
