package za.org.grassroot.webapp.controller.rest.broadcast;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.springframework.format.annotation.DateTimeFormat;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@NoArgsConstructor @Getter @Setter @ToString
public class BroadcastCreateRequest {

    private String broadcastId;
    private String title;

    private boolean sendShortMessages; // SMS / GCM / WhatsApp, in time
    private String shortMessageString;

    private boolean sendEmail;
    private String emailContent;
    private List<String> emailAttachmentKeys;

    private boolean postToFacebook;
    private List<String> facebookPages;
    private String facebookContent;
    private String facebookLink;
    private String facebookLinkCaption;
    private String facebookImageKey;

    private boolean postToTwitter;
    private String twitterContent; // as above, need to think through media more
    private String twitterLink;
    private String twitterLinkCaption;
    private String twitterImageKey;

    private BroadcastSchedule sendType;
    private Long sendDateTimeMillis;

    private List<Province> provinces;
    private Boolean noProvince;
    private List<String> topics;
    private List<String> affiliations;
    private List<String> taskTeams;
    private List<GroupJoinMethod> joinMethods;
    private List<Locale> filterLanguages;

    private String filterNamePhoneEmail;

    private boolean skipSmsIfEmail;

    private JoinDateCondition joinDateCondition;
    private @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate joinDate;

    public Instant getSendDateTime() {
        return sendDateTimeMillis == null ? null : Instant.ofEpochMilli(sendDateTimeMillis);
    }

}
