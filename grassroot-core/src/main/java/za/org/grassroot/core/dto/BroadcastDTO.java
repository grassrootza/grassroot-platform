package za.org.grassroot.core.dto;

import lombok.Getter;
import lombok.Setter;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.util.List;

@Getter @Setter
public class BroadcastDTO {

    private String title;
    private BroadcastSchedule scheduleType;

    private boolean shortMessageSent;
    private boolean emailSent;

    private long smsCount;
    private long emailCount;
    private String fbPage;
    private String twitterAccount;

    private Instant dateTimeSent;
    private Instant scheduledSendTime;

    private float costEstimate;

    private String smsContent;
    private String emailContent;
    private String fbPost;
    private String twitterPost;

    private List<Province> provinces;
    private List<String> topics;

    public BroadcastDTO(Broadcast broadcast, long deliveredSmsCount, long deliveredEmailCount, float costEstimate) {
        // set things up
        this.title = broadcast.getTitle();
        this.scheduleType = broadcast.getBroadcastSchedule();

        this.shortMessageSent = !StringUtils.isEmpty(broadcast.getSmsTemplate1());
        this.emailSent = !StringUtils.isEmpty(broadcast.getEmailContent());

        this.smsCount = deliveredSmsCount;
        this.emailCount = deliveredEmailCount;
        this.costEstimate = costEstimate;

        this.dateTimeSent = broadcast.getSentTime();
        this.scheduledSendTime = broadcast.getScheduledSendTime();

        this.smsContent = broadcast.getSmsTemplate1();
        this.emailContent = broadcast.getEmailContent();
        this.fbPost = broadcast.getFacebookPost();
        this.twitterPost = broadcast.getTwitterPost();
    }

}
