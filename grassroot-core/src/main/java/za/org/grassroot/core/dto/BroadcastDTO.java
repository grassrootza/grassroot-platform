package za.org.grassroot.core.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.enums.Province;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Getter @Setter @JsonInclude(JsonInclude.Include.NON_NULL)
public class BroadcastDTO {

    private String broadcastUid;
    private String title;
    private BroadcastSchedule scheduleType;

    private boolean succeeded;
    private boolean createdByUser;

    private boolean shortMessageSent;
    private boolean emailSent;

    private long smsCount;
    private long emailCount;

    private Long dateTimeSentMillis;
    private Long scheduledSendTimeMillis;

    private float costEstimate;

    private String smsContent;
    private String emailContent;
    private String fbPost;
    private List<String> fbPages;
    private String twitterPost;
    private String twitterAccount;

    private boolean hasFilter;
    private List<Province> provinces;
    private List<String> topics;
    private List<String> taskTeamNames;
    private List<String> affiliations;
    private List<GroupJoinMethod> joinMethods;
    private List<Locale> languages;
    private JoinDateCondition joinDateCondition;
    private LocalDate joinDate;

    public BroadcastDTO(Broadcast broadcast, boolean createdByUser, boolean succeeded) {
        // set things up
        this.broadcastUid = broadcast.getUid();
        this.title = broadcast.getTitle();
        this.scheduleType = broadcast.getBroadcastSchedule();
        this.succeeded = succeeded;
        this.createdByUser = createdByUser;

        this.shortMessageSent = !StringUtils.isEmpty(broadcast.getSmsTemplate1());
        this.emailSent = !StringUtils.isEmpty(broadcast.getEmailContent());

        this.dateTimeSentMillis = broadcast.getSentTime() != null ? broadcast.getSentTime().toEpochMilli() : null;
        this.scheduledSendTimeMillis = broadcast.getScheduledSendTime() != null ? broadcast.getScheduledSendTime().toEpochMilli() : null;

        this.smsContent = broadcast.getSmsTemplate1();
        this.emailContent = broadcast.getEmailContent();
        this.fbPost = broadcast.getFacebookPost();
        this.twitterPost = broadcast.getTwitterPost();

        this.hasFilter = broadcast.hasFilter();

        if (this.hasFilter) {
            this.provinces = broadcast.getProvinces();
            this.topics = broadcast.getTopics();
            this.taskTeamNames = broadcast.getTaskTeams();
            this.affiliations = broadcast.getAffiliations();
            this.joinMethods = broadcast.getJoinMethods();
            this.joinDateCondition = broadcast.getJoinDateCondition().orElse(null);
            this.joinDate = broadcast.getJoinDate().orElse(null);
        }
    }

    @Override
    public String toString() {
        return "BroadcastDTO{" +
                "broadcastUid='" + broadcastUid + '\'' +
                ", title='" + title + '\'' +
                ", succeeded='" + succeeded + '\'' +
                ", smsCount=" + smsCount +
                ", emailCount=" + emailCount +
                ", twitterAccount=" + twitterAccount +
                '}';
    }
}
