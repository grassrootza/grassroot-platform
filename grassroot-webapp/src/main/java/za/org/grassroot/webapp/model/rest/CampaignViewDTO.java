package za.org.grassroot.webapp.model.rest;

import java.io.Serializable;
import java.util.List;


public class CampaignViewDTO implements Serializable{

    private static final long serialVersionUID = -2316086319788909407L;
    private String createUserName;
    private String createUserUid;
    private String campaignName;
    private String campaignCode;
    private String campaignDescription;
    private String campaignStartDate;
    private String campaignEndDate;
    private String campaignType;
    private String campaignUrl;
    private String campaignUid;
    private String campaignMasterGroupUid;
    private String campaignMasterGroupName;
    private Integer totalUsers;
    private Integer newUsers;
    private List<String> campaignTags;
    private List<CampaignMessageViewDTO> campaignMessages;

    public CampaignViewDTO(String campaignCode, String campaignName,String campaignDescription, String campaignType,
                           String createUserUid, String createUserName, String campaignStartDate, String campaignEndDate, String campaignUrl, String campaignUid, List<String> campaignTags){
        this.campaignCode = campaignCode;
        this.campaignName = campaignName;
        this.campaignDescription = campaignDescription;
        this.campaignType = campaignType;
        this.createUserName = createUserName;
        this.createUserUid = createUserUid;
        this.campaignStartDate = campaignStartDate;
        this.campaignEndDate = campaignEndDate;
        this.campaignUid = campaignUid;
        this.campaignUrl = campaignUrl;
        this.campaignTags = campaignTags;
    }

    public String getCreateUserName() {
        return createUserName;
    }

    public void setCreateUserName(String createUserName) {
        this.createUserName = createUserName;
    }

    public String getCreateUserUid() {
        return createUserUid;
    }

    public void setCreateUserUid(String createUserUid) {
        this.createUserUid = createUserUid;
    }

    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    public String getCampaignCode() {
        return campaignCode;
    }

    public void setCampaignCode(String campaignCode) {
        this.campaignCode = campaignCode;
    }

    public String getCampaignDescription() {
        return campaignDescription;
    }

    public void setCampaignDescription(String campaignDescription) {
        this.campaignDescription = campaignDescription;
    }

    public String getCampaignStartDate() {
        return campaignStartDate;
    }

    public void setCampaignStartDate(String campaignStartDate) {
        this.campaignStartDate = campaignStartDate;
    }

    public String getCampaignEndDate() {
        return campaignEndDate;
    }

    public void setCampaignEndDate(String campaignEndDate) {
        this.campaignEndDate = campaignEndDate;
    }

    public String getCampaignUrl() {
        return campaignUrl;
    }

    public void setCampaignUrl(String campaignUrl) {
        this.campaignUrl = campaignUrl;
    }

    public String getCampaignMasterGroupUid() {
        return campaignMasterGroupUid;
    }

    public void setCampaignMasterGroupUid(String campaignMasterGroupUid) {
        this.campaignMasterGroupUid = campaignMasterGroupUid;
    }

    public String getCampaignMasterGroupName() {
        return campaignMasterGroupName;
    }

    public void setCampaignMasterGroupName(String campaignMasterGroupName) {
        this.campaignMasterGroupName = campaignMasterGroupName;
    }

    public Integer getTotalUsers() {
        return totalUsers;
    }

    public void setTotalUsers(Integer totalUsers) {
        this.totalUsers = totalUsers;
    }

    public Integer getNewUsers() {
        return newUsers;
    }

    public void setNewUsers(Integer newUsers) {
        this.newUsers = newUsers;
    }

    public List<String> getCampaignTags() {
        return campaignTags;
    }

    public void setCampaignTags(List<String> campaignTags) {
        this.campaignTags = campaignTags;
    }

    public String getCampaignType() {
        return campaignType;
    }

    public void setCampaignType(String campaignType) {
        this.campaignType = campaignType;
    }

    public String getCampaignUid() {
        return campaignUid;
    }

    public void setCampaignUid(String campaignUid) {
        this.campaignUid = campaignUid;
    }

    public List<CampaignMessageViewDTO> getCampaignMessages() {
        return campaignMessages;
    }

    public void setCampaignMessages(List<CampaignMessageViewDTO> campaignMessages) {
        this.campaignMessages = campaignMessages;
    }
}
