package za.org.grassroot.core.domain.campaign;

import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Type;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.TagHolder;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.media.MediaFileRecord;
import za.org.grassroot.core.enums.CampaignLogType;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.io.Serializable;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Entity @Getter @Setter
@Table(name = "campaign")
public class Campaign implements Serializable, Comparable<Campaign>, TagHolder {

    public static final String JOIN_TOPIC_PREFIX = "JOIN_TOPIC_";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Version
    @Column(name = "version",nullable = false)
    private Integer version;

    @Column(name = "uid", nullable = false, unique = true, updatable = false)
    private String uid;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "description", length = 512)
    private String description;

    @Column(name = "code", length = 5)
    private String campaignCode;

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
    private Group masterGroup;

    @ManyToOne
    @JoinColumn(name = "account_uid", referencedColumnName = "uid")
    private Account account;

    @OneToMany(cascade = CascadeType.ALL, mappedBy = "campaign")
    private Set<CampaignMessage> campaignMessages = new HashSet<>();

    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    private String[] tags;

    @Enumerated(EnumType.STRING)
    @Column(name = "type",nullable = false)
    private CampaignType campaignType;

    @Column(name = "landing_url",nullable = true)
    private String landingUrl;

    @Column(name = "petition_api")
    private String petitionApi;

    @Column(name = "petition_result_api")
    private String petitionResultApi;

    @OneToMany(mappedBy = "campaign")
    private Set<CampaignLog> campaignLogs = new HashSet<>();

    @Column(name = "sharing_enabled")
    private boolean sharingEnabled = false;

    @Column(name = "sharing_budget")
    private long sharingBudget; // in cents

    @Column(name = "sharing_spent")
    private long sharingSpent; // in cents, also can be calculated from notification count, but double checking (also as price per may alter)

    @ManyToOne
    @JoinColumn(name = "image_record_uid", referencedColumnName = "uid")
    private MediaFileRecord campaignImage;

    public Campaign() {
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
    }

    public Campaign(String campaignName, String campaignCode,String campaignDescription, User createdByUser, Instant startDateTime, Instant endDateTime,CampaignType campaignType, String campaignUrl){
        this.uid = UIDGenerator.generateId();
        this.createdDateTime = Instant.now();
        this.name = Objects.requireNonNull(campaignName);
        this.campaignCode = Objects.requireNonNull(campaignCode);
        this.createdByUser = Objects.requireNonNull(createdByUser);
        this.description = Objects.requireNonNull(campaignDescription);
        this.startDateTime = Objects.requireNonNull(startDateTime);
        this.endDateTime = Objects.requireNonNull(endDateTime);
        this.campaignType = Objects.requireNonNull(campaignType);
        this.landingUrl = campaignUrl;
        this.sharingEnabled = false;
        this.sharingBudget = 0L;
        this.sharingSpent = 0L;
    }

    public boolean isActive() {
        return Instant.now().isBefore(endDateTime);
    }

    @Override
    public String[] getTags(){
        return tags;
    }

    @Override
    public void setTags(String[] tags) {
        this.tags = tags;
    }

    public List<String> getJoinTopics() {
        return this.getTagList().stream().filter(s -> s.startsWith(JOIN_TOPIC_PREFIX))
                .map(s -> s.substring(JOIN_TOPIC_PREFIX.length())).collect(Collectors.toList());
    }

    public void setAffiliations(Set<String> joinTopics) {
        // first get all the non-affiliation tags
        List<String> tags = getTagList().stream()
                .filter(s -> !s.startsWith(JOIN_TOPIC_PREFIX)).collect(Collectors.toList());
        // then add the topics
        tags.addAll(joinTopics.stream().map(s -> JOIN_TOPIC_PREFIX + s).collect(Collectors.toSet()));
        setTags(tags);
    }

    public long countUsersInLogs(CampaignLogType logType) {
        return getCampaignLogs().stream()
                .filter(log -> logType.equals(log.getCampaignLogType()))
                .map(log -> log.getUser().getId())
                .distinct()
                .count();
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
        sb.append(", name='").append(name).append('\'');
        sb.append(", campaignCode='").append(campaignCode).append('\'');
        sb.append(", createdDateTime=").append(createdDateTime);
        sb.append(", createdBy=").append(createdByUser.getId());
        sb.append(", campaignStartDate=").append(startDateTime);
        sb.append(", campaignEndDate=").append(endDateTime);
        sb.append(", campaignType=").append(campaignType);
        sb.append(", version=").append(version);
        sb.append('}');
        return sb.toString();
    }

}
