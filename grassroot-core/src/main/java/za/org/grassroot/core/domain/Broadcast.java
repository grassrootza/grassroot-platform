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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Class to hold a broadcast, which we are going to use quite a lot
 */
@Entity
@Table(name = "broadcast")
@Getter @Builder
public class Broadcast implements GrassrootEntity, TagHolder {

    public static final int MAX_MESSAGES = 3;

    private static final String PROVINCE_PREFIX = "PROVINCE:";
    private static final String TASK_TEAM_PREFIX = "TASK_TEAM:";
    private static final String AFFIL_PREFIX = "AFFILIATION:";
    private static final String JOIN_METHOD_PREFIX = "JOIN_METHOD:";
    private static final String JOIN_DATE_CONDITION_PREFIX = "JOIN_DATE_CONDITION:";
    private static final String JOIN_DATE_PREFIX = "JOIN_DATE_VALUE:";

    public static String NAME_FIELD_TEMPLATE = "{__name__}";
    public static String CONTACT_FIELD_TEMPALTE = "{__contact__}";
    public static String DATE_FIELD_TEMPLATE = "{__date__}";
    public static String PROVINCE_FIELD_TEMPLATE = "{__province__}";

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

    public String getEmailIncludingMerge(User destination, DateTimeFormatter dtf, String noProvince,
                                         Map<Province, String> provinceNames) {
        if (StringUtils.isEmpty(emailContent)) {
            return "";
        }

        return mergeTemplate(destination, emailContent, dtf, noProvince, provinceNames);
    }

    public String getShortMsgIncludingMerge(User dest, DateTimeFormatter dtf, String noProvince, Map<Province, String> provinceNames) {
        if (StringUtils.isEmpty(smsTemplate1)) {
            return "";
        }

        return mergeTemplate(dest, smsTemplate1, dtf, noProvince, provinceNames);
    }

    private String mergeTemplate(User destination, String template, DateTimeFormatter dtf, String noProvince, Map<Province, String> provinceNames) {
        final String formatString = template
                .replace(NAME_FIELD_TEMPLATE, "%1$s")
                .replace(CONTACT_FIELD_TEMPALTE, "%2$s")
                .replace(DATE_FIELD_TEMPLATE, "%3$s")
                .replace(PROVINCE_FIELD_TEMPLATE, "%4$s");

        return String.format(formatString, destination.getName(), destination.getUsername(), dtf.format(LocalDateTime.now()),
                destination.getProvince() == null ? noProvince : provinceNames.getOrDefault(destination.getProvince(), noProvince))
                .trim().replaceAll(" +", " ");
    }

    // using this to preserve builder pattern (note: deviates from prior style of setting these in constructor,
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

    public boolean hasFilter() {
        return !getTaskTeams().isEmpty() || !getProvinces().isEmpty() || !getTaskTeams().isEmpty()
                || !getTopics().isEmpty() || !getAffiliations().isEmpty() || !getJoinMethods().isEmpty() || getJoinDateCondition().isPresent();
    }

    public List<Province> getProvinces() {
        return getFilterEntitiesFromTag(PROVINCE_PREFIX)
                .map(Province::valueOf)
                .collect(Collectors.toList());
    }

    public List<String> getTaskTeams() {
        return getFilterEntitiesFromTag(TASK_TEAM_PREFIX).collect(Collectors.toList());
    }

    public List<GroupJoinMethod> getJoinMethods() {
        return getFilterEntitiesFromTag(JOIN_METHOD_PREFIX).map(GroupJoinMethod::valueOf).collect(Collectors.toList());
    }

    public List<String> getAffiliations() {
        return getFilterEntitiesFromTag(AFFIL_PREFIX).collect(Collectors.toList());
    }

    public Optional<JoinDateCondition> getJoinDateCondition() {
        return getFilterEntitiesFromTag(JOIN_DATE_CONDITION_PREFIX).findFirst().map(JoinDateCondition::valueOf);
    }

    public Optional<LocalDate> getJoinDate() {
        return getFilterEntitiesFromTag(JOIN_DATE_PREFIX).findFirst().map(s -> LocalDate.parse(s, DateTimeFormatter.ISO_DATE));
    }

    private Stream<String> getFilterEntitiesFromTag(String prefix) {
        return getTagList().stream().filter(s -> s.startsWith(prefix)).map(s -> s.substring(prefix.length()));
    }

    public void setProvinces(Collection<Province> provinces) {
        setFilterEntities(PROVINCE_PREFIX, provinces.stream().map(Enum::name));
    }

    public void setTaskTeams(Collection<String> taskTeamUids) {
        setFilterEntities(TASK_TEAM_PREFIX, taskTeamUids.stream());
    }

    public void setJoinMethods(Collection<GroupJoinMethod> joinMethods) {
        setFilterEntities(JOIN_METHOD_PREFIX, joinMethods.stream().map(Enum::name));
    }

    public void setAffiliations(Collection<String> affiliations) {
        setFilterEntities(AFFIL_PREFIX, affiliations.stream());
    }

    public void setJoinDateCondition(JoinDateCondition condition) {
        setFilterEntities(JOIN_DATE_CONDITION_PREFIX, Stream.of(condition.name()));
    }

    public void setJoinDateValue(LocalDate joinDateValue) {
        setFilterEntities(JOIN_DATE_PREFIX, Stream.of(String.valueOf(joinDateValue.format(DateTimeFormatter.ISO_DATE))));
    }

    private void setFilterEntities(String prefix, Stream<String> entities) {
        List<String> nonPrefixTags = getTagList().stream()
                .filter(s -> !s.startsWith(prefix)).collect(Collectors.toList());
        nonPrefixTags.addAll(entities.map(s -> prefix + s).collect(Collectors.toList()));
        setTags(nonPrefixTags);
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
