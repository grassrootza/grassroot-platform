package za.org.grassroot.core.domain.campaign;


import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "campaign_log", uniqueConstraints =
@UniqueConstraint(name = "uk_campaign_log_uid", columnNames = "uid"))
public class CampaignLog implements ActionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name="id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, length = 50)
    private String uid;

    @Column(name="creation_time", nullable = false, updatable = false)
    private Instant creationTime;

    @Enumerated(EnumType.STRING)
    @Column(name="campaign_log_type", nullable = false, length = 50)
    private CampaignLogType campaignLogType;

    @Column(name="user_uid", nullable = false)
    private String userUid;

    @ManyToOne
    @JoinColumn(name="campaign_id", nullable = false)
    private Campaign campaign;

    @Column(name="description", nullable = false)
    private String description;

    public CampaignLog(String userUid, CampaignLogType campaignLogType,Campaign campaign) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.userUid = userUid;
        this.campaignLogType = Objects.requireNonNull(campaignLogType);
        this.campaign = Objects.requireNonNull(campaign);
    }

    public CampaignLog(String userUid, CampaignLogType campaignLogType, String searchValue) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.userUid = userUid;
        this.campaignLogType = Objects.requireNonNull(campaignLogType);
        this.description = Objects.requireNonNull(searchValue);
    }


    @Override
    public int hashCode() {
        return (getUid() != null) ? getUid().hashCode() : 0;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) { return true; }
        if (o == null || getClass() != o.getClass()) { return false; }

        final CampaignLog that = (CampaignLog) o;

        return getUid() != null ? getUid().equals(that.getUid()) : that.getUid() == null;

    }

    @Override
    public String toString() {
        return "CampaignLog{" +
                "id=" + id +
                ", campaignLogType=" + campaignLogType +
                ", userUid=" + userUid +
                ", creationTime =" + creationTime +
                '}';
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    @Override
    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Instant getCreationTime() {
        return creationTime;
    }

    public void setCreationTime(Instant creationTime) {
        this.creationTime = creationTime;
    }


    public String getUserUid() {
        return userUid;
    }

    public void setUserUid(String userUid) {
        this.userUid = userUid;
    }

    public CampaignLogType getCampaignLogType() {
        return campaignLogType;
    }

    public void setCampaignLogType(CampaignLogType campaignLogType) {
        this.campaignLogType = campaignLogType;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
