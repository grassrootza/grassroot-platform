package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.campaign.Campaign;
import za.org.grassroot.core.domain.group.Group;

import java.util.List;

public interface BroadcastRepository extends JpaRepository<Broadcast, Integer>, JpaSpecificationExecutor<Broadcast> {

    Broadcast findOneByUid(String uid);

    Broadcast findTopByGroupAndBroadcastScheduleAndActiveTrue(Group group, BroadcastSchedule broadcastType);

    Broadcast findTopByCampaignAndBroadcastScheduleAndActiveTrue(Campaign campaign, BroadcastSchedule broadcastSchedule);

    List<Broadcast> findByGroup(Group group); // note: will also fetch historical

    Page<Broadcast> findByGroupUidAndSentTimeNotNull(String groupUid, Pageable pageable);

    Page<Broadcast> findByGroupUidAndSentTimeIsNullAndBroadcastSchedule(String groupUid, BroadcastSchedule broadcastSchedule, Pageable pageable);

    List<Broadcast> findByCampaign(Campaign campaign);

}
