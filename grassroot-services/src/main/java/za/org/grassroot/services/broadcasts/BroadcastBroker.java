package za.org.grassroot.services.broadcasts;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.dto.BroadcastDTO;
import za.org.grassroot.integration.socialmedia.FBPostBuilder;
import za.org.grassroot.integration.socialmedia.TwitterPostBuilder;

import java.util.List;

public interface BroadcastBroker {

    BroadcastInfo fetchGroupBroadcastParams(String userUid, String groupUid);

    String sendGroupBroadcast(BroadcastComponents broadcastComponents);

    BroadcastDTO fetchBroadcast(String broadcastUid);

    Page<BroadcastDTO> fetchSentGroupBroadcasts(String groupUid, Pageable pageable);

    Page<BroadcastDTO> fetchScheduledGroupBroadcasts(String groupUid, Pageable pageable);

    List<BroadcastDTO> fetchCampaignBroadcasts(String campaignUid);

}
