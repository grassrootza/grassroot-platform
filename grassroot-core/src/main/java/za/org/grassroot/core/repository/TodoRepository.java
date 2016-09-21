package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke. Most variation moved into services layer as specifications, as of 15/9/2016
 */
public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    Todo findOneByUid(String uid);

    // methods for analyzing action/to-do (for admin)
    Long countByCreatedDateTimeBetween(Instant start, Instant end);

    @Query(value = "select l.* from action_todo l " +
        "inner join group_profile g on l.parent_group_id = g.id " +
        "inner join group_user_membership m on g.id = m.group_id " +
        "where m.user_id = ?1 and to_tsvector('english', l.message) @@ to_tsquery('english', ?2)", nativeQuery = true)
    List<Todo> findByParentGroupMembershipsUserAndMessageSearchTerm(Long userId, String tsQueryText);

    // todo : consider switching this to specifications as well
    @Transactional
    @Query(value = "select td from Todo td " +
            "left join td.completionConfirmations cm " +
            "where td.cancelled = false " +
            "and td.completionPercentage < ?2 " +
            "and td.numberOfRemindersLeftToSend > 0 " +
            "and td.scheduledReminderTime < ?1 " +
            "and td.reminderActive = true " +
            "and (cm.member is null or cm.member != td.createdByUser)")
    List<Todo> findAllTodosForReminding(Instant referenceInstant, double threshold);

    // these are in test only and have been superceded in the main code -- delete once test coverage for services built out
    List<Todo> findByParentGroupAndCancelledFalse(Group group);
    List<Todo> findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(User user, Instant start, Instant end, double minCompletionPercentage, Sort sort);

    // methods for handling replication
    List<Todo> findByParentGroupAndMessageAndCreatedDateTime(Group group, String message, Instant createdDateTime);
    List<Todo> findByReplicatedGroupAndMessageAndActionByDateOrderByParentGroupIdAsc(Group replicatedGroup, String message, Instant actionByDateTime);



    @Query(value = "select count(t) from Todo t where t.replicatedGroup=?1 and t.message=?2 and t.actionByDate=?3")
    int countReplicatedEntries(Group group, String message, Instant actionByDate);
}
