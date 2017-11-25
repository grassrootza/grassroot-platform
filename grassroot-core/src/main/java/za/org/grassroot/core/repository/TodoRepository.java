package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Created by luke. Most variation moved into services layer as specifications, as of 15/9/2016
 */
public interface TodoRepository extends JpaRepository<Todo, Long>, JpaSpecificationExecutor<Todo> {

    Todo findOneByUid(String uid);
    Set<Todo> findByUidIn(Collection<String> uids);

    // methods for analyzing action/to-do (for admin)
    Long countByCreatedDateTimeBetween(Instant start, Instant end);

    @Query(value = "select l.* from action_todo l " +
        "inner join group_profile g on l.parent_group_id = g.id " +
        "inner join group_user_membership m on g.id = m.group_id " +
        "where m.user_id = ?1 and to_tsvector('english', l.message) @@ to_tsquery('english', ?2)", nativeQuery = true)
    List<Todo> findByParentGroupMembershipsUserAndMessageSearchTerm(Long userId, String tsQueryText);

    // these are in test only and have been superceded in the main code -- delete once test coverage for services built out
    List<Todo> findByAssignmentsUserAndActionByDateBetweenAndCompletedTrue(User user, Instant start, Instant end, Sort sort);

    @Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
            "t.uid, 'TODO', tl.createdDateTime) from " +
            "TodoLog tl inner join tl.todo t " +
            "inner join t.ancestorGroup g inner join g.memberships m " +
            "where m.user = ?1 and " +
            "(tl.createdDateTime = (select max(tll.createdDateTime) from TodoLog tll where tll.todo = t))")
    List<TaskTimeChangedDTO> fetchTodosWithTimeChangedForUser(User user);


    @Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
            "t.uid, 'TODO', tl.createdDateTime) from " +
            "TodoLog tl inner join tl.todo t " +
            "where t.ancestorGroup = ?1 and " +
            "(tl.createdDateTime = (select max(tll.createdDateTime) from TodoLog tll where tll.todo = t))")
    List<TaskTimeChangedDTO> fetchGroupTodosWithTimeChanged(Group group);

    @Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
            "t.uid, 'TODO', tl.createdDateTime) from " +
            "TodoLog tl inner join tl.todo t " +
            "where t.uid in ?1 and " +
            "(tl.createdDateTime = (select max(tll.createdDateTime) from TodoLog tll where tll.todo = t))")
    List<TaskTimeChangedDTO> fetchTodosWithTimeChanged(Set<String> taskUids);

}
