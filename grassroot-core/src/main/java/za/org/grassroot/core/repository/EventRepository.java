package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

	Event findOneByUid(String uid);

	Event findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(Group group);

	List<Event> findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant start, Instant end, Sort sort);

	List<Event> findByParentGroupAndCanceledFalse(Group group);

    int countByParentGroupMembershipsUserAndEventStartDateTimeGreaterThan(User user, Instant instant);

    /*
    A set of queries to use in fetching events related to a user (different flavors exist)
     */
	List<Event> findByParentGroupMembershipsUser(User user);

	List<Event> findByParentGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(User user, Instant start);

	/*
	Method to find events that a user is part of and where the title includes a search term
	 todo: modify to rather use assigned_members, but still pick up if that is empty (i.e., all group or parent members assigned)
	 todo: also search descriptions
	 todo: this may be quite slow (i.e., to adjust as part of general tune-up / improvement of these search methods)
	 */
	List<Event> findByParentGroupMembershipsUserAndNameContainingIgnoreCase(User user, String searchTerm);

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
	@Query(value = "select e from Event e where e.canceled = false and eventStartDateTime > ?1 and e.scheduledReminderTime < ?1 and e.scheduledReminderActive = true")
	List<Event> findEventsForReminders(Instant fromInstant);

	@Query(value = "select v from Vote v where v.eventStartDateTime > ?1 and v.canceled = false")
	List<Event> findAllVotesAfterTimeStamp(Instant fromInstant);

	@Query("SELECT e from EventLog el inner join el.event e where e.parentGroup = ?1 and el.eventLogType = 'CANCELLED' AND el.createdDateTime >= ?2")
	List<Event> findByParentGroupAndCanceledSince(Group group, Instant since);
}
