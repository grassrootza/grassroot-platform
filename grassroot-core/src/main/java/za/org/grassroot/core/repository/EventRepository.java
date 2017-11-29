package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Set;

public interface EventRepository extends JpaRepository<Event, Long>, JpaSpecificationExecutor<Event> {

	Event findOneByUid(String uid);

	Set<Event> findByUidIn(Collection<String> uids);

	Event findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(Group group);

	List<Event> findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant start, Instant end, Sort sort);

	List<Event> findByParentGroupAndCanceledFalse(Group group);

    int countByParentGroupMembershipsUserAndEventStartDateTimeGreaterThan(User user, Instant instant);

	Event findOneByCreatedByUserAndParentGroupAndNameAndEventStartDateTimeBetweenAndCanceledFalse(User user, Group group, String name, Instant startDateTime, Instant endDateTime);

	@Query(value = "select e.* from event e " +
			"inner join group_profile g on e.parent_group_id = g.id " +
			"inner join group_user_membership m on g.id = m.group_id " +
			"where m.user_id = ?1 and to_tsvector('english', e.name) @@ to_tsquery('english', ?2)", nativeQuery = true)
	List<Event> findByParentGroupMembershipsUserAndNameSearchTerm(Long userId, String tsQueryText);

	/*

	N.B. do not remove start_date_time > current_timestamp as it will force the query to do an
	index scan, when there is enough data

select * from event e
where e.canceled = FALSE
	  and start_date_time > current_timestamp -- index for start_date_time and so we can read by index - local timestamp???
	  and (start_date_time - e.reminderminutes * INTERVAL '1 minute') < current_timestamp
	  and (start_date_time - e.reminderminutes * INTERVAL '1 minute') > e.created_date_time
	  and e.reminderminutes > 0
	  and e.noreminderssent = 0

	 */
	@Transactional
	@Query(value = "select e from Event e " +
			"where e.canceled = false " +
			"and e.eventStartDateTime > ?1 " +
			"and e.scheduledReminderTime < ?1 " +
			"and e.scheduledReminderActive = true")
	List<Event> findEventsForReminders(Instant referenceInstant);

	@Query(value = "select v from Vote v where v.eventStartDateTime > ?1 and v.canceled = false")
	List<Event> findAllVotesAfterTimeStamp(Instant fromInstant);

	@Query("SELECT e from EventLog el inner join el.event e where e.parentGroup = ?1 and el.eventLogType = 'CANCELLED' AND el.createdDateTime >= ?2")
	List<Event> findByParentGroupAndCanceledSince(Group group, Instant since);

	@Query("SELECT e from EventLog el " +
			"inner join el.event e " +
			"inner join e.parentGroup g " +
			"inner join g.memberships m " +
			"where el.eventLogType = 'CANCELLED' and m.user = ?1 and el.createdDateTime >= ?2")
	List<Event> findByMemberAndCanceledSince(User user, Instant since);

	// note : if assignment filtering gets heavy, integrate it in this query
	@Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
			"e.uid, type(e), el.createdDateTime) from " +
			"EventLog el inner join el.event e " +
			"inner join e.ancestorGroup g inner join g.memberships m " +
			"where m.user = ?1 and " +
			"(el.createdDateTime = (select max(ell.createdDateTime) from EventLog ell where ell.event = e))")
	List<TaskTimeChangedDTO> fetchEventsWithTimeChangedForUser(User user);

	@Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
			"e.uid, type(e), el.createdDateTime) from " +
			"EventLog el inner join el.event e " +
			"where e.ancestorGroup = ?1 and " +
			"(el.createdDateTime = (select max(ell.createdDateTime) from EventLog ell where ell.event = e))")
	List<TaskTimeChangedDTO> fetchGroupEventsWithTimeChanged(Group group);

	@Query("select new za.org.grassroot.core.dto.task.TaskTimeChangedDTO(" +
			"e.uid, type(e), el.createdDateTime) from " +
			"EventLog el " +
			"inner join el.event e " +
			"where e.uid in ?1 and " +
			"(el.createdDateTime = (select max(ell.createdDateTime) from EventLog ell where ell.event = e))")
	List<TaskTimeChangedDTO> fetchEventsWithTimeChanged(Set<String> taskUids);

}
