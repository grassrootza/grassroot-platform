package za.org.grassroot.webapp.controller.rest.broadcast;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.enums.Province;

import java.time.Instant;
import java.util.List;

@NoArgsConstructor @Getter @Setter
public class BroadcastCreateRequest {

    private String title;

    private boolean sendShortMessages; // SMS / GCM / WhatsApp, in time
    private String shortMessageString;

    private boolean sendEmail;
    private String emailContent;

    private boolean postToFacebook;
    private String facebookContent;
    private String facebookLink; // will need to think through media

    private boolean postToTwitter;
    private String twitterContent; // as above, need to think through media more

    private BroadcastSchedule sendType;
    private Long sendDateTimeMills;

    private List<Province> provinces;
    private List<String> topics;

    public Instant getSendDateTime() {
        return sendDateTimeMills == null ? null : Instant.ofEpochMilli(sendDateTimeMills);
    }

}
