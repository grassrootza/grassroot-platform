package za.org.grassroot.core.domain;

import org.hibernate.annotations.Type;
import za.org.grassroot.core.enums.MessageVariationAssignment;
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
import javax.persistence.Version;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "campaign_message")
public class CampaignMessage implements Serializable, Comparable<CampaignMessage>, TagHolder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Version
    @Column(name = "version",nullable = false)
    private Integer version;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Column(name = "message", nullable = false, unique = true)
    private String message;

    @Column(name = "created_date_time", insertable = true, updatable = false)
    private Instant createdDateTime;

    @ManyToOne()
    @JoinColumn(name = "created_by_user", nullable = false, updatable = false)
    private User createdByUser;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "variation")
    private MessageVariationAssignment variation;

    @Column(name = "locale")
    private String locale;

    public CampaignMessage(String message, User createdByUser, MessageVariationAssignment variation, String locale){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.variation = Objects.requireNonNull(variation);
        this.message = Objects.requireNonNull(message);
        this.createdByUser = Objects.requireNonNull(createdByUser);
        this.locale = Objects.requireNonNull(locale);
    }

    @Override
    public String[]getTags(){
        return tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || !(o instanceof CampaignMessage)) {
            return false;
        }
        CampaignMessage message = (CampaignMessage) o;
        return (getUid() != null) ? getUid().equals(message.getUid()) : message.getUid() == null;

    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public int compareTo(CampaignMessage message) {
        if (uid.equals(message.getUid())) {
            return 0;
        } else {
            Instant otherCreatedDateTime = message.getCreatedDateTime();
            return createdDateTime.compareTo(otherCreatedDateTime);
        }
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder("CampaignMessage{");
        sb.append("id=").append(id);
        sb.append(", uid='").append(uid).append('\'');
        sb.append(", message='").append(message).append('\'');
        sb.append(", createdDateTime=").append(createdDateTime);
        sb.append(", createdBy=").append(createdByUser.getId());
        sb.append(", locale=").append(locale);
        sb.append(", variation=").append(variation.name());
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

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedDateTime() {
        return createdDateTime;
    }

    public void setCreatedDateTime(Instant createdDateTime) {
        this.createdDateTime = createdDateTime;
    }

    public Campaign getCampaign() {
        return campaign;
    }

    public void setCampaign(Campaign campaign) {
        this.campaign = campaign;
    }

    public User getCreatedByUser() {
        return createdByUser;
    }

    public void setCreatedByUser(User createdByUser) {
        this.createdByUser = createdByUser;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public MessageVariationAssignment getVariation() {
        return variation;
    }

    public void setVariation(MessageVariationAssignment variation) {
        this.variation = variation;
    }

    public String getLocale() {
        return locale;
    }

    public void setLocale(String locale) {
        this.locale = locale;
    }
}
