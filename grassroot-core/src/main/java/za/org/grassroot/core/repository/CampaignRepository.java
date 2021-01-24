package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.campaign.Campaign;

import java.time.Instant;
import java.util.List;
import java.util.Set;


public interface CampaignRepository extends JpaRepository<Campaign, Long>, JpaSpecificationExecutor<Campaign> {

    Campaign findOneByUid(String uid);

    Campaign findByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);
    long countByCampaignCodeAndEndDateTimeAfter(String campaignCode, Instant date);

    List<Campaign> findByMasterGroupUid(String groupUid, Sort sort);
    long countByMasterGroupUidAndEndDateTimeAfter(String groupUid, Instant endDate);

    @Query(value = "select * from campaign where ?1 = ANY(tags) and end_date_time::date > now()", nativeQuery = true)
    Campaign findActiveCampaignByTag(String tag);

    @Query(value = "select unnest(tags), uid from campaign " +
            "where end_date_time > current_timestamp group by uid, tags", nativeQuery = true)
    List<Object[]> fetchAllActiveCampaignTags();

    @Query(value = "select c.campaignCode from Campaign c where c.endDateTime > current_timestamp")
    Set<String> fetchAllActiveCampaignCodes();

    @Query(value = "select c.* from campaign c where " +
            "(to_tsvector('english', c.name) @@ to_tsquery('english', ?1))", nativeQuery = true)
    List<Campaign> findCampaignsWithNamesIncluding(String tsQuery);

    @Query(value = "SELECT c.* FROM campaign c " +
            "WHERE c.created_by_user = :userId " +
            "OR c.ancestor_group_id IN " +
            "   (" +
            "       SELECT m.group_id FROM group_user_membership m " +
            "       WHERE m.user_id = :userId AND m.role = 'ROLE_GROUP_ORGANIZER'" +
            "   ) " +
            "ORDER BY c.created_date_time ASC", nativeQuery = true)
    List<Campaign> findCampaignsManagedByUser(@Param(value = "userId") Long userId);

}
