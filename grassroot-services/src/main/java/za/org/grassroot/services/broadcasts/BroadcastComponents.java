package za.org.grassroot.services.broadcasts;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.JoinDateCondition;
import za.org.grassroot.core.enums.Province;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

@Getter @Builder
public class BroadcastComponents {

    private String broadcastId;

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

    @Setter private List<FBPostBuilder> facebookPosts;
    @Setter private TwitterPostBuilder twitterPostBuilder;

    private List<String> topics;
    private List<Province> provinces;
    private Boolean noProvince;
    private List<String> taskTeams;
    private List<String> affiliations;
    private List<GroupJoinMethod> joinMethods;
    private JoinDateCondition joinDateCondition;
    private List<Locale> filterLanguages;
    private String filterNamePhoneOrEmail;
    private @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    LocalDate joinDate;

    public boolean isImmediateBroadcast() {
        return broadcastSchedule == null || broadcastSchedule.equals(BroadcastSchedule.IMMEDIATE);
    }

}
