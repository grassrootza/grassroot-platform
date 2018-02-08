package za.org.grassroot.webapp.controller.rest.broadcast;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.JoinDateCondition;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

@NoArgsConstructor @Getter @Setter @ToString
public class BroadcastCreateRequest {

    private String broadcastId;
    private String title;

    private boolean sendShortMessages; // SMS / GCM / WhatsApp, in time
    private String shortMessageString;

    private boolean sendEmail;
    private String emailContent;

    private boolean postToFacebook;
    private List<String> facebookPages;
    private String facebookContent;
    private String facebookLink; // will need to think through media
    private String facebookLinkCaption;

    private boolean postToTwitter;
    private String twitterContent; // as above, need to think through media more
    private String twitterLink;
    private String twitterLinkCaption;

    private BroadcastSchedule sendType;
    private Long sendDateTimeMills;

    private List<Province> provinces;
    private List<String> topics;
    private List<String> affiliations;
    private List<String> taskTeams;
    private List<GroupJoinMethod> joinMethods;

    private JoinDateCondition joinDateCondition;
    private @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDate;

    public Instant getSendDateTime() {
        return sendDateTimeMills == null ? null : Instant.ofEpochMilli(sendDateTimeMills);
    }

}
