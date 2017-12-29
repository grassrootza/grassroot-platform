package za.org.grassroot.services.broadcasts;

import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;

import java.util.List;

public interface BroadcastBroker {

    String sendGroupBroadcast(BroadcastComponents broadcastComponents);

    BroadcastDTO fetchBroadcast(String broadcastUid);

    List<BroadcastDTO> fetchGroupBroadcasts(String groupUid);

    List<BroadcastDTO> fetchCampaignBroadcasts(String campaignUid);

}
