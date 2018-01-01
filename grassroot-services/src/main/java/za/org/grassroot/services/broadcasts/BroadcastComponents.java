package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;

import java.time.Instant;
import java.util.List;

@Getter @Builder
public class BroadcastComponents {

    private boolean campaignBroadcast;

    private BroadcastSchedule broadcastSchedule;
    private Instant scheduledSendTime;

    private String userUid;
    private String groupUid;
    private String campaignUid;

    private String title;

    @Setter private String shortMessage;
    @Setter private EmailBroadcast email;

    @Setter private boolean useOnlyFreeChannels;
    @Setter private boolean skipSmsIfEmail;

    @Setter private FBPostBuilder facebookPost;
    @Setter private TwitterPostBuilder twitterPostBuilder;

    private List<String> topics;
    private List<Province> provinces;

    public boolean isImmediateBroadcast() {
        return broadcastSchedule == null || broadcastSchedule.equals(BroadcastSchedule.IMMEDIATE);
    }

}
