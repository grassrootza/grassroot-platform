package za.org.grassroot.core.domain.campaign;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.annotations.Type;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.MessageVariationAssignment;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.LocaleConverter;
import za.org.grassroot.core.util.StringArrayUtil;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity @Getter @Setter @Slf4j
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

    @Basic
    @Column(name = "active")
    private boolean active;

    @Column(name = "deactive_time")
    private Instant deactivatedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private CampaignActionType actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel",nullable = false)
    private UserInterfaceType channel;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    private Campaign campaign;

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "variation")
    private MessageVariationAssignment variation;

    @Convert(converter = LocaleConverter.class)
    @Column(name = "locale")
    private Locale locale;

    @OneToOne(cascade = CascadeType.ALL, mappedBy = "actionMessage")
    private CampaignMessageAction parentAction;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "parentCampaignMessage", orphanRemoval = true)
    private Set<CampaignMessageAction> campaignMessageActionSet = new HashSet<>();

    @Column(name = "next_actions")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] nextActions;

    private CampaignMessage(){
        // for JPA
    }

    public CampaignMessage(User createdByUser, Campaign campaign, Locale locale, String message, UserInterfaceType channel, MessageVariationAssignment variation, CampaignActionType actionType){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.variation = variation == null ? MessageVariationAssignment.DEFAULT : variation;
        this.message = Objects.requireNonNull(message);
        this.createdByUser = Objects.requireNonNull(createdByUser);
        this.locale = Objects.requireNonNull(locale);
        this.channel = channel == null ? UserInterfaceType.USSD : channel;
        this.campaign = Objects.requireNonNull(campaign);
        this.active = true;
        this.actionType = actionType;
    }

    public void addNextMessage(String nextMsgUid, CampaignActionType actionType) {
        List<String> actions = StringArrayUtil.arrayToList(this.nextActions);
        actions.add(nextMsgUid + ":" + actionType.name());
        this.nextActions = StringArrayUtil.listToArray(actions);
    }

    public Map<String, CampaignActionType> getNextMessages() {
        List<String> actions = StringArrayUtil.arrayToList(nextActions);
        Map<String, CampaignActionType> unsortedMap = actions.stream().collect(Collectors.toMap(
                s -> s.substring(0, s.indexOf(":")),
                s -> CampaignActionType.valueOf(s.substring(s.indexOf(":") + 1))));
        return unsortedMap.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (ov, nv) -> ov, LinkedHashMap::new));
    }

    @Override
    public String[]getTags(){
        return tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public boolean matchesMessageAndLocale(Campaign campaign, final String message, Locale locale) {
        return this.campaign.equals(campaign) && this.message.equals(message.trim()) && this.locale.equals(locale);
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
        return (this.getUid() != null) ? this.getUid().equals(message.getUid()) : false;

    }

    @Override
    public int hashCode() {
        return getUid() != null ? getUid().hashCode() : 0;
    }

    @Override
    public int compareTo(CampaignMessage message) {
        if (uid.equals(message.getUid())) {
            return 0;
        } else if (actionType.equals(message.getActionType())) {
            Instant otherCreatedDateTime = message.getCreatedDateTime();
            return createdDateTime.compareTo(otherCreatedDateTime);
        } else {
            return actionType.compareTo(message.getActionType());
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
        sb.append(", channel=").append(channel);
        sb.append(", sequence number").append(channel);
        sb.append(", variation=").append(variation.name());
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }

    public Set<CampaignMessageAction> getCampaignMessageActionSet() {
        if(campaignMessageActionSet == null){
            campaignMessageActionSet = new HashSet<>();
        }
        return campaignMessageActionSet;
    }

    public void setCampaignMessageActionSet(Set<CampaignMessageAction> campaignMessageActionSet) {
        this.campaignMessageActionSet = campaignMessageActionSet;
    }

}
