package za.org.grassroot.core.domain;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Tolerate;
import org.hibernate.annotations.Type;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Class to hold a broadcast, which we are going to use quite a lot
 */
@Entity
@Table(name = "broadcast")
@Getter @Builder
public class Broadcast implements GrassrootEntity, TagHolder {

    public static final int MAX_MESSAGES = 3;

    private static final String PROVINCE_PREFIX = "PROVINCE:";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "uid", nullable = false, unique = true)
    private String uid;

    @Basic
    @Column(name = "title")
    @Setter private String title;

    @Column(name = "creation_time", insertable = true, updatable = false)
    private Instant creationTime;

    @ManyToOne
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdByUser;

    /*
    Section: timing / scheduling of broadcast
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "broadcast_schedule", length = 50, nullable = false)
    private BroadcastSchedule broadcastSchedule;

    @Column(name = "scheduled_send_time")
    private Instant scheduledSendTime;

    @Column(name = "sent_time")
    @Setter private Instant sentTime;

    @Basic
    @Column(name = "active")
    @Setter private boolean active;

    /*
    Section: the meat of it: contents, and some options
     */
    @Basic
    @Column(name = "only_use_free")
    @Setter private boolean onlyUseFreeChannels;

    @Basic
    @Column(name = "skip_sms_if_email")
    @Setter private boolean skipSmsIfEmail; // ie if user has both email and sms, only send former (cost saving)

    @Basic
    @Column(name = "msg_template1")
    @Setter private String smsTemplate1;

    @Basic
    @Column(name = "msg_template2")
    @Setter private String smsTemplate2;

    @Basic
    @Column(name = "msg_template3")
    @Setter private String smsTemplate3;

    @Basic
    @Column(name = "email_content")
    @Setter private String emailContent;

    @Basic
    @Column(name = "email_image_key")
    @Setter private String emailImageKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "email_delivery_route", length = 50)
    @Setter private DeliveryRoute emailDeliveryRoute;

    @Basic
    @Column(name = "facebook_page_id")
    @Setter private String facebookPageId;

    @Basic
    @Column(name = "facebook_post")
    @Setter private String facebookPost;

    @Basic
    @Column(name = "facebook_link_url")
    @Setter private String facebookLinkUrl;

    @Basic
    @Column(name = "facebook_link_name")
    @Setter private String facebookLinkName;

    @Basic
    @Column(name = "facebook_image_key")
    @Setter private String facebookImageKey;

    @Basic
    @Column(name = "facebook_image_caption")
    @Setter private String facebookImageCaption;

    @Basic
    @Column(name = "facebook_post_succeeded")
    @Setter private Boolean fbPostSucceeded;

    @Basic
    @Column(name = "twitter_post", length = 240)
    @Setter private String twitterPost;

    @Basic
    @Column(name = "twitter_image_key")
    @Setter private String twitterImageKey;

    @Basic
    @Column(name = "twitter_post_succeeded")
    @Setter private Boolean twitterSucceeded;

    @Basic
    @Column(name = "send_delay")
    @Setter private Long delayIntervalMillis;

    @ManyToOne
    @JoinColumn(name = "account_id", nullable = false)
    @Setter private Account account;

    @ManyToOne
    @JoinColumn(name = "group_id")
    @Setter private Group group;

    @ManyToOne
    @JoinColumn(name = "campaign_id")
    @Setter private Campaign campaign;

    @Basic
    @Column(name = "cascade")
    @Setter @Builder.Default private boolean cascade = false;

    // note: we use this for storing both provinces _and_ topics (using PROVINCE: and TOPICS:)
    @Column(name = "tags")
    @Type(type = "za.org.grassroot.core.util.StringArrayUserType")
    @Setter private String[] tags;


    @Tolerate
    private Broadcast() {
        // for JPA
    }

    @Override
    public String getName() {
        return title;
    }

    // using this to preserve builder pattern (note: deviates from prior style of setting these in constructor,
    // todo is consider reconciling to old way by more sophisticated use of Builder pattern
    @PrePersist
    private void setDefaults() {
        if (uid == null) {
            uid = UIDGenerator.generateId();
        }
        if (creationTime == null) {
            creationTime = Instant.now();
        }
    }

    public boolean hasShortMessage() {
        return !StringUtils.isEmpty(smsTemplate1);
    }

    public boolean hasEmail() {
        return !StringUtils.isEmpty(emailContent);
    }

    public boolean hasFbPost() {
        return !StringUtils.isEmpty(facebookPageId) && !StringUtils.isEmpty(facebookPost);
    }

    public boolean hasTwitterPost() {
        return !StringUtils.isEmpty(twitterPost);
    }

    public List<Province> getProvinces() {
        return getTagList().stream()
                .filter(s -> s.startsWith(PROVINCE_PREFIX))
                .map(s -> s.substring(PROVINCE_PREFIX.length()))
                .map(Province::valueOf)
                .collect(Collectors.toList());
    }

    public void setProvinces(Set<Province> provinces) {
        List<String> tags = getTagList().stream()
                .filter(s -> !s.startsWith(PROVINCE_PREFIX)).collect(Collectors.toList());
        tags.addAll(provinces.stream().map(s -> PROVINCE_PREFIX + s.name()).collect(Collectors.toSet()));
        setTags(tags);
    }

    // used in thymeleaf hence preserved here
    public List<String> getTemplateStrings() {
        List<String> templates = new ArrayList<>();
        templates.add(smsTemplate1);
        if (!StringUtils.isEmpty(smsTemplate2)) {
            templates.add(smsTemplate2);
        }
        if (!StringUtils.isEmpty(smsTemplate3)) {
            templates.add(smsTemplate3);
        }
        return templates;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Broadcast template = (Broadcast) o;

        return uid.equals(template.uid);
    }

    @Override
    public int hashCode() {
        return uid.hashCode();
    }
}
