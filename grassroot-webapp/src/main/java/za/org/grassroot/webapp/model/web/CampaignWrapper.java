package za.org.grassroot.webapp.model.web;

import org.hibernate.validator.constraints.NotBlank;

import java.io.Serializable;
import java.util.Set;


public class CampaignWrapper implements Serializable {

    private static final long serialVersionUID = -3263387305104374730L;
    private String userUid;
    private String name;
    private String code;
    private String description;
    private String startDate;
    private String endDate;
    private Set<String> tags;

    public CampaignWrapper(){}

    @NotBlank(message = "{campaign.user.uid.required}")
    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    @NotBlank(message = "{campaign.name.required}")
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @NotBlank(message = "{campaign.code.required}")
    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    @NotBlank(message = "{campaign.description.required}")
    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    @NotBlank(message = "{campaign.start.date.required}")
    public String getStartDate() {
        return startDate;
    }

    public void setStartDate(String startDate) {
        this.startDate = startDate;
    }

    @NotBlank(message = "{campaign.end.date.required}")
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
}
