package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.BroadcastType;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.campaign.Campaign;

import java.util.List;
import java.util.Set;

public interface BroadcastRepository extends JpaRepository<Broadcast, Integer> {

    Broadcast findTopByGroupAndBroadcastTypeAndActiveTrue(Group group, BroadcastType broadcastType);

    List<Broadcast> findByGroupAndActiveTrue(Group group); // note: will also fetch historical

    List<Broadcast> findByCampaignAndActiveTrue(Campaign campaign);

}
