package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface TodoRepository extends JpaRepository<Todo, Long> {

    Todo findOneByUid(String uid);

    // todo: this amount of complex query methods is bad; should redesign in some way
    // todo: (maybe using Spring Data JPA Specification to compose the query programmatically !?)

    /*
    Retrieve all action/to-do entries for all the groups of a particular user
     */
    List<Todo> findByParentGroupMembershipsUserAndActionByDateGreaterThanAndCancelledFalse(User user, Instant start);
    List<Todo> findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageLessThanAndCancelledFalse(User user, Instant start, Instant end, double maxCompletionPercentage, Sort sort);

    Page<Todo> findByParentGroupMembershipsUserAndCompletionPercentageGreaterThanEqualAndCancelledFalseOrderByActionByDateDesc(User user, double minCompletionPercentage, Pageable pageable);
    Page<Todo> findByParentGroupMembershipsUserAndCompletionPercentageLessThanOrderByActionByDateDesc(User user, double maxCompletionPercentage, Pageable pageable);

    /*
    Retrieve all action/to-do entries assigned to a particular user
     */
    List<Todo> findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(User user, Instant start, Instant end, double minCompletionPercentage, Sort sort);
    List<Todo> findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageLessThan(User user, Instant start, Instant end, double maxCompletionPercentage, Sort sort);

    /*
    Retrieve action/to-do entries for a group (with variants)
     */
    List<Todo> findByParentGroupAndCancelledFalse(Group group);
    List<Todo> findByParentGroupAndCreatedDateTimeBetween(Group group, Instant start, Instant end, Sort sort);
    List<Todo> findByParentGroupAndActionByDateGreaterThanAndCancelledFalse(Group group, Instant dueDate);
    List<Todo> findByParentGroupAndCompletionPercentageGreaterThanEqualAndActionByDateGreaterThanAndCancelledFalse(Group group, double minCompletionPercentage, Instant dueDate);
    List<Todo> findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThanAndCancelledFalse(Group group, double maxCompletionPercentage, Instant dueDate);

    Page<Todo> findByParentGroupAndCompletionPercentageGreaterThanEqualAndCancelledFalseOrderByActionByDateDesc(Group group, double minCompletionPercentage, Pageable pageable);
    Page<Todo> findByParentGroupAndCompletionPercentageLessThanAndCancelledFalseOrderByActionByDateDesc(Group group, double maxCompletionPercentage, Pageable pageable);

    // methods for handling replication
    List<Todo> findByParentGroupAndMessageAndCreatedDateTime(Group group, String message, Instant createdDateTime);
    List<Todo> findByReplicatedGroupAndMessageAndActionByDateOrderByParentGroupIdAsc(Group replicatedGroup, String message, Instant actionByDateTime);

    // methods for analyzing action/to-do (for admin)
    Long countByCreatedDateTimeBetween(Instant start, Instant end);

    @Query(value = "select l.* from action_todo l " +
        "inner join group_profile g on l.parent_group_id = g.id " +
        "inner join group_user_membership m on g.id = m.group_id " +
        "where m.user_id = ?1 and to_tsvector('english', l.message) @@ to_tsquery('english', ?2)", nativeQuery = true)
    List<Todo> findByParentGroupMembershipsUserAndMessageSearchTerm(Long userId, String tsQueryText);

    @Transactional
    @Query(value = "select td from Todo td " +
            "where td.cancelled = false " +
            "and td.completionPercentage < " + Todo.COMPLETION_PERCENTAGE_BOUNDARY + " " +
            "and td.numberOfRemindersLeftToSend > 0 " +
            "and td.scheduledReminderTime < ?1 " +
            "and td.reminderActive = true")
    List<Todo> findAllTodosForReminding(Instant referenceInstant);

    @Query(value = "select count(t) from Todo t where t.replicatedGroup=?1 and t.message=?2 and t.actionByDate=?3")
    int countReplicatedEntries(Group group, String message, Instant actionByDate);

}
