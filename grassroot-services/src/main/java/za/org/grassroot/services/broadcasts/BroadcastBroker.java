package za.org.grassroot.services.broadcasts;

import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;

import java.util.List;

public interface BroadcastBroker {

    void sendGroupBroadcast(String userUid, String groupUid, String campaignUid,
                            List<String> smsMessages, EmailBroadcast email, FBPostBuilder facebookPost,
                            TwitterPostBuilder twitterPostBuilder);

    List<Broadcast> fetchGroupBroadcasts(String groupUid);

    List<Broadcast> fetchCampaignBroadcasts(String campaignUid);

}
