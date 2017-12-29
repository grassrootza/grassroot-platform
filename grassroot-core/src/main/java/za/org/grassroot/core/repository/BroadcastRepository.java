package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Broadcast;
import za.org.grassroot.core.domain.BroadcastSchedule;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.campaign.Campaign;

import java.util.List;

public interface BroadcastRepository extends JpaRepository<Broadcast, Integer> {

    Broadcast findOneByUid(String uid);

    Broadcast findTopByGroupAndBroadcastScheduleAndActiveTrue(Group group, BroadcastSchedule broadcastType);

    List<Broadcast> findByGroup(Group group); // note: will also fetch historical

    List<Broadcast> findByCampaign(Campaign campaign);

}
