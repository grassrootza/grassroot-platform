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
    Retrieve all logbook entries for all the groups of a particular user
     */
    List<Todo> findByParentGroupMembershipsUserAndActionByDateGreaterThan(User user, Instant start);
    List<Todo> findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageLessThan(User user, Instant start, Instant end, double maxCompletionPercentage, Sort sort);

    Page<Todo> findByParentGroupMembershipsUserAndCompletionPercentageGreaterThanEqualOrderByActionByDateDesc(User user, double minCompletionPercentage, Pageable pageable);
    Page<Todo> findByParentGroupMembershipsUserAndCompletionPercentageLessThanOrderByActionByDateDesc(User user, double maxCompletionPercentage, Pageable pageable);

    /*
    Retrieve all logbook entries assigned to a particular user
     */
    List<Todo> findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(User user, Instant start, Instant end, double minCompletionPercentage, Sort sort);
    List<Todo> findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageLessThan(User user, Instant start, Instant end, double maxCompletionPercentage, Sort sort);

    /*
    Retrieve logbook entries for a group (with variants)
     */
    List<Todo> findByParentGroup(Group group);
    List<Todo> findByParentGroupAndCreatedDateTimeBetween(Group group, Instant start, Instant end, Sort sort);
    List<Todo> findByParentGroupAndMessageAndCreatedDateTime(Group group, String message, Instant createdDateTime);
    List<Todo> findByParentGroupAndActionByDateGreaterThan(Group group, Instant dueDate);
    List<Todo> findByParentGroupAndCompletionPercentageGreaterThanEqualAndActionByDateGreaterThan(Group group, double minCompletionPercentage, Instant dueDate);
    List<Todo> findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThan(Group group, double maxCompletionPercentage, Instant dueDate);

    Page<Todo> findByParentGroupAndCompletionPercentageGreaterThanEqualOrderByActionByDateDesc(Group group, double minCompletionPercentage, Pageable pageable);
    Page<Todo> findByParentGroupAndCompletionPercentageLessThanOrderByActionByDateDesc(Group group, double maxCompletionPercentage, Pageable pageable);

    List<Todo> findByReplicatedGroupAndMessageAndActionByDateOrderByParentGroupIdAsc(Group replicatedGroup, String message, Instant actionByDateTime);

    // methods for analyzing logbooks (for admin)
    Long countByCreatedDateTimeBetween(Instant start, Instant end);

    @Query(value = "select l.* from log_book l " +
        "inner join group_profile g on l.parent_group_id = g.id " +
        "inner join group_user_membership m on g.id = m.group_id " +
        "where m.user_id = ?1 and to_tsvector('english', l.message) @@ to_tsquery('english', ?2)", nativeQuery = true)
    List<Todo> findByParentGroupMembershipsUserAndMessageSearchTerm(Long userId, String tsQueryText);

    @Transactional
    @Query(value = "select * from log_book l where l.action_by_date is not null and l.completion_percentage < " + Todo.COMPLETION_PERCENTAGE_BOUNDARY + " and l.number_of_reminders_left_to_send > 0 and (l.action_by_date + l.reminder_minutes * INTERVAL '1 minute') < current_timestamp", nativeQuery = true)
    List<Todo> findAllLogBooksForReminding();

    @Query(value = "select count(t) from Todo t where t.replicatedGroup=?1 and t.message=?2 and t.actionByDate=?3")
    int countReplicatedEntries(Group group, String message, Instant actionByDate);

}
