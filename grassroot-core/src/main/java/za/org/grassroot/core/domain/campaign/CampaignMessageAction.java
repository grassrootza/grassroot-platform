package za.org.grassroot.core.domain.campaign;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.CascadeType;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.OneToOne;
import javax.persistence.Table;
import javax.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "campaign_message_action")
public class CampaignMessageAction implements Serializable, Comparable<CampaignMessageAction>{
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version",nullable = false)
    private Integer version;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "action_message_id")
    private CampaignMessage actionMessage;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @Enumerated(EnumType.STRING)
    @Column(name = "action")
    private CampaignActionType actionType;

    @ManyToOne
    @JoinColumn(name = "parent_message_id")
    private CampaignMessage parentCampaignMessage;

    public CampaignMessageAction(){}

    public CampaignMessageAction(CampaignMessage parentCampaignMessage,CampaignMessage actionMessage,CampaignActionType actionType, User createdByUser){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.actionMessage = actionMessage;
        this.parentCampaignMessage = parentCampaignMessage;
        this.actionType = Objects.requireNonNull(actionType);
        this.createdByUser = Objects.requireNonNull(createdByUser);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof CampaignMessageAction)) {
            return false;
        }
        CampaignMessageAction action = (CampaignMessageAction) o;
        return (getUid() != null) ? getUid().equals(action.getUid()) : action.getUid() == null;
    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public int compareTo(CampaignMessageAction action) {
        if (uid.equals(action.getUid())) {
            return 0;
        } else {
            Instant otherCreatedDateTime = action.getCreatedDateTime();
            return createdDateTime.compareTo(otherCreatedDateTime);
        }
    }


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public String getUid() {
        return uid;
    }

    public void setUid(String uid) {
        this.uid = uid;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public CampaignActionType getActionType() {
        return actionType;
    }

    public void setActionType(CampaignActionType actionType) {
        this.actionType = actionType;
    }

    public CampaignMessage getParentCampaignMessage() {
        return parentCampaignMessage;
    }

    public void setParentCampaignMessage(CampaignMessage parentCampaignMessage) {
        this.parentCampaignMessage = parentCampaignMessage;
    }

    public CampaignMessage getActionMessage() {
        return actionMessage;
    }

    public void setActionMessage(CampaignMessage actionMessage) {
        this.actionMessage = actionMessage;
    }
}
