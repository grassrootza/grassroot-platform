package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import java.time.Instant;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

	Event findOneByUid(String uid);

	Event findTopByAppliesToGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(Group group);

	List<Event> findByAppliesToGroupAndEventStartDateTimeBetweenAndCanceledFalse(Group group, Instant start, Instant end, Sort sort);

    int countByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThan(User user, Instant instant);

    /*
    A set of queries to use in fetching events related to a user (different flavors exist)
     */
	List<Event> findByAppliesToGroupMembershipsUser(User user);

	List<Event> findByAppliesToGroupMembershipsUserAndEventStartDateTimeGreaterThanAndCanceledFalse(User user, Instant start);

	List<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceledFalse(User user, Instant startTime);

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
	List<Event> findEventsForReminders(Instant fromInstant);

	@Query(value = "select v from Vote v where v.eventStartDateTime > ?1 and v.canceled = false")
	List<Event> findAllVotesAfterTimeStamp(Instant fromInstant);

    /* (Some old queries, leaving in, just in case SQL statements come in handy in the future)

    @Query(value = "SELECT * FROM event e WHERE applies_to_group IN (SELECT group_id FROM group_user_membership ")

	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.canceled = :canceled order by e.EventStartDateTime desc")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndCanceledOrderByEventStartDateTimeDesc(User user, Class<? extends Event> eventClass, boolean canceled);

	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.eventStartDateTime > :startTime and e.canceled = :canceled")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeGreaterThanAndCanceled(User user, Class<? extends Event> eventClass, Date startTime, boolean cancelled);

	@Query(value = "select e from Event e where e.appliesToGroup.memberships.user = :user and e.class = :eventClass and e.eventStartDateTime < :startTime and e.canceled = :canceled")
	List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeLessThanAndCanceled(User user, Class<? extends Event> eventClass, Date startTime, boolean cancelled);

    @Query(value = "select count(e) from Event e where e.class = :eventClass and e.eventStartDateTime is not null")
	Long countByEventTypeAndEventStartDateTimeNotNull(Class<? extends Event> eventClass);

	@Query(value = "select count(e) from Event e where e.class = :eventClass and e.createdDateTime between :start and :end and e.eventStartDateTime is not null")
	int countByEventTypeAndCreatedDateTimeBetween(EventType eventType, Timestamp start, Timestamp end);


*/

}
