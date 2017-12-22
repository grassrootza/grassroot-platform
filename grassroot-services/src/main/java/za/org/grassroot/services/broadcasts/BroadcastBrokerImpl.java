package za.org.grassroot.services.broadcasts;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.repository.BroadcastRepository;
import za.org.grassroot.core.repository.CampaignRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;

import java.util.List;

@Service
public class BroadcastBrokerImpl implements BroadcastBroker {

    private final BroadcastRepository broadcastRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final CampaignRepository campaignRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public BroadcastBrokerImpl(BroadcastRepository broadcastRepository, UserRepository userRepository, GroupRepository groupRepository, CampaignRepository campaignRepository, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.broadcastRepository = broadcastRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.campaignRepository = campaignRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Override
    public void sendGroupBroadcast(String userUid, String groupUid, String campaignUid, List<String> smsMessages, EmailBroadcast email, FBPostBuilder facebookPost, TwitterPostBuilder twitterPostBuilder) {
        // todo: complete this ... going to be hairy ...
        User user = userRepository.findOneByUid(userUid);
    }

    @Override
    public List<Broadcast> fetchGroupBroadcasts(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return broadcastRepository.findByGroupAndActiveTrue(group);
    }

    @Override
    public List<Broadcast> fetchCampaignBroadcasts(String campaignUid) {
        Campaign campaign = campaignRepository.findOneByUid(campaignUid);
        return broadcastRepository.findByCampaignAndActiveTrue(campaign);
    }
}
