package za.org.grassroot.core.domain.campaign;


import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.ActionLogType;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity @Getter
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

    @Enumerated(EnumType.STRING)
    @Column(name="user_interface_channel", length = 50)
    private UserInterfaceType channel;

    @ManyToOne(optional = true)
    @JoinColumn(name="user_id")
    private User user;

    @ManyToOne
    @JoinColumn(name="campaign_id", nullable = true)
    private Campaign campaign;

    @Column(name="description", nullable = true)
    @Setter private String description;

    @ManyToOne
    @JoinColumn(name = "broadcast_id")
    @Setter private Broadcast broadcast;

    private CampaignLog() {
        // for JPA
    }

    public CampaignLog(User user, CampaignLogType campaignLogType, Campaign campaign, UserInterfaceType channel, String description) {
        this.uid = UIDGenerator.generateId();
        this.creationTime = Instant.now();
        this.user = user;
        this.campaignLogType = Objects.requireNonNull(campaignLogType);
        this.campaign = Objects.requireNonNull(campaign);
        this.channel = channel;
        this.description = description;
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
                "campaignLogType=" + campaignLogType +
                ", user=" + user.getName() +
                '}';
    }

    @Override
    public String getUid() {
        return uid;
    }

    @Override
    public ActionLogType getActionLogType() {
        return ActionLogType.CAMPAIGN_LOG;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }
}
