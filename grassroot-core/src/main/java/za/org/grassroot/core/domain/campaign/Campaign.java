package za.org.grassroot.core.domain.campaign;

import org.hibernate.annotations.Type;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToMany;
import javax.persistence.Table;
import javax.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

@Entity
@Table(name = "campaign")
public class Campaign implements Serializable, Comparable<Campaign>, TagHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version",nullable = false)
    private Integer version;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "name", nullable = false, length = 50)
    private String campaignName;

    @Column(name = "code", nullable = false, length = 3)
    private String campaignCode;

    @Column(name = "description")
    private String campaignDescription;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @Column(name = "start_date_time", insertable = true, updatable = false)
    private Instant startDateTime;

    @Column(name = "end_date_time", insertable = true, updatable = false)
    private Instant endDateTime;

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "ancestor_group_id", nullable = true)
    private Group masterGroup ;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "campaign", orphanRemoval = true)
    private Set<CampaignMessage> campaignMessages;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    public Campaign(String campaignName, String campaignCode,String campaignDescription, User createdByUser, Instant startDateTime, Instant endDateTime){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.campaignName = Objects.requireNonNull(campaignName);
        this.campaignCode = Objects.requireNonNull(campaignCode);
        this.createdByUser = Objects.requireNonNull(createdByUser);
        this.campaignDescription = Objects.requireNonNull(campaignDescription);
        this.startDateTime = Objects.requireNonNull(startDateTime);
        this.endDateTime = Objects.requireNonNull(endDateTime);
    }

    @Override
    public String[]getTags(){
        return tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof Campaign)) {
            return false;
        }

        Campaign campaign = (Campaign) o;

        return (getUid() != null) ? getUid().equals(campaign.getUid()) : campaign.getUid() == null;

    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public int compareTo(Campaign campaign) {
        if (uid.equals(campaign.getUid())) {
            return 0;
        } else {
            Instant otherCreatedDateTime = campaign.getCreatedDateTime();
            return createdDateTime.compareTo(otherCreatedDateTime);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("Campaign{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", campaignName='").append(campaignName).append('\'');
        sb.append(", campaignCode='").append(campaignCode).append('\'');
        sb.append(", createdDateTime=").append(createdDateTime);
        sb.append(", createdBy=").append(createdByUser.getId());
        sb.append(", campaignStartDate=").append(startDateTime);
        sb.append(", campaignEndDate=").append(endDateTime);
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
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

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Instant getStartDateTime() {
        return startDateTime;
    }

    public void setStartDateTime(Instant startDateTime) {
        this.startDateTime = startDateTime;
    }

    public Instant getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(Instant endDateTime) {
        this.endDateTime = endDateTime;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }


    public Set<CampaignMessage> getCampaignMessages() {
        if(this.campaignMessages == null){
            this.campaignMessages = new HashSet<>();
        }
        return campaignMessages;
    }

    public void setCampaignMessages(Set<CampaignMessage> campaignMessages) {
        this.campaignMessages = campaignMessages;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getCampaignDescription() {
        return campaignDescription;
    }

    public void setCampaignDescription(String campaignDescription) {
        this.campaignDescription = campaignDescription;
    }

    public Group getMasterGroup() {
        return masterGroup;
    }

    public void setMasterGroup(Group masterGroup) {
        this.masterGroup = masterGroup;
    }
}
