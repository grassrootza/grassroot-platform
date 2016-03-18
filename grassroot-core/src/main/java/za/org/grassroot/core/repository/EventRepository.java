package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

	Event findOneByUid(String uid);

	List<Event> findByAppliesToGroup(Group group);

	List<Event> findByAppliesToGroupOrderByEventStartDateTimeDesc(Group group);

	List<Event> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(Group group, Date startTime, boolean cancelled);
//    List<Event> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceledAndEventType(Group group, Date startTime, boolean cancelled, EventType eventType);

	List<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled);

	Page<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled, Pageable page);

	/*
	Some methods for analytical services, to count, find events, etc
	 */
	@Query(value = "select count(e) from Event e where e.class = :eventClass and e.eventStartDateTime is not null")
	Long countByEventTypeAndEventStartDateTimeNotNull(Class<? extends Event> eventClass);

	@Query(value = "select count(e) from Event e where e.class = :eventClass and e.createdDateTime between :start and :end and e.eventStartDateTime is not null")
	int countByEventTypeAndCreatedDateTimeBetweenAndEventStartDateTimeNotNull(Class<? extends Event> eventClass, Timestamp start, Timestamp end);


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
	@Query(value = "select e from Event e where e.canceled = false and eventStartDateTime > ?1 and e.scheduledReminderTime < ?1 and e.scheduledReminderActive = true")
	List<Event> findEventsForReminders(Date time);

	@Query(value = "select v from Vote v where v.eventStartDateTime > ?1 and v.canceled = false")
	List<Event> findAllVotesAfterTimeStamp(Date date);

	@Query(value = "SELECT * FROM event e WHERE start_date_time  between  (current_timestamp - INTERVAL '1 hour') and current_timestamp AND e.type = 'VOTE' AND e.canceled = FALSE AND (SELECT count(*) FROM event_log el WHERE el.event_log_type = 7 AND e.id = el.event_id) = 0", nativeQuery = true)
	List<Event> findUnsentVoteResults();

	@Query(value = "SELECT count(*) from Event e where e.start_date_time > current_timestamp and e.applies_to_group in (select group_id from group_user_membership where user_id = ?1)", nativeQuery = true)
	int countFutureEvents(Long userId);


	/*
	Some queries to find a user's events : leaving query stub in, as the property traversal by JPA may be expensive, and may want to do counts later
	 */
	// @Query(value = "SELECT * FROM event e WHERE applies_to_group IN (SELECT group_id FROM group_user_membership ")
	List<Event> findByAppliesToGroupMembershipsUser(User user);

/*
	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.canceled = :canceled order by e.EventStartDateTime desc")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndCanceledOrderByEventStartDateTimeDesc(User user, Class<? extends Event> eventClass, boolean canceled);

	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.eventStartDateTime > :startTime and e.canceled = :canceled")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeGreaterThanAndCanceled(User user, Class<? extends Event> eventClass, Date startTime, boolean cancelled);

	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.eventStartDateTime < :startTime and e.canceled = :canceled")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeLessThanAndCanceled(User user, Class<? extends Event> eventClass, Date startTime, boolean cancelled);
*/

	/*
	Queries for analysis, i.e., counting and retrieving how many events took place in a certain period
	 */
	List<Event> findByAppliesToGroupAndEventStartDateTimeBetween(Group group, Timestamp start, Timestamp end, Sort sort);
}
