package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Date;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    Event findOneByUid(String uid);

    List<Event> findByAppliesToGroup(Group group);
    List<Event> findByAppliesToGroupOrderByEventStartDateTimeDesc(Group group);

    List<Event> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceled(Group group, Date startTime, boolean cancelled);
    List<Event> findByAppliesToGroupAndEventStartDateTimeGreaterThanAndCanceledAndEventType(Group group, Date startTime, boolean cancelled, EventType eventType);

    List<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled);
    Page<Event> findByCreatedByUserAndEventStartDateTimeGreaterThanAndCanceled(User user, Date startTime, boolean cancelled, Pageable page);

    /*
    Some methods for analytical services, to count, find events, etc
     */
    Long countByEventTypeAndEventStartDateTimeNotNull(EventType eventType);
    int countByEventTypeAndCreatedDateTimeBetweenAndEventStartDateTimeNotNull(EventType type, Timestamp start, Timestamp end);


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
    @Query(value = "select e from event e where e.canceled = false and eventStartDateTime > :time and e.scheduledReminderTime < :time and e.scheduledReminderActive = true")
    List<Event> findEventsForReminders(Instant time);

    @Query(value = "select v from Event v where v.eventType = za.org.grassroot.core.enums.EventType.Vote and v.eventStartDateTime > ?1 and v.canceled = false")
    List<Event> findAllVotesAfterTimeStamp(Date date);

    @Query(value = "SELECT * FROM event e WHERE start_date_time  between  (current_timestamp - INTERVAL '1 hour') and current_timestamp AND e.event_type = 1 AND e.canceled = FALSE AND (SELECT count(*) FROM event_log el WHERE el.event_log_type = 7 AND e.id = el.event_id) = 0", nativeQuery = true)
    List<Event> findUnsentVoteResults();

    @Query(value = "SELECT count(*) from Event e where e.start_date_time > current_timestamp and e.applies_to_group in (select group_id from group_user_membership where user_id = ?1)", nativeQuery = true)
    int countFutureEvents(Long userId);



    /*
    Some queries to find a user's events : leaving query stub in, as the property traversal by JPA may be expensive, and may want to do counts later
     */
    // @Query(value = "SELECT * FROM event e WHERE applies_to_group IN (SELECT group_id FROM group_user_membership ")
    List<Event> findByAppliesToGroupMembershipsUser(User user);
    List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndCanceledOrderByEventStartDateTimeDesc(User user, EventType type, boolean canceled);
    List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeGreaterThanAndCanceled(User user, EventType eventType, Date startTime, boolean cancelled);
    List<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeLessThanAndCanceled(User user, EventType eventType, Date starTime, boolean cancelled);

    Page<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndCanceledOrderByEventStartDateTimeDesc(User user, EventType type, boolean canceled, Pageable page);
    Page<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeGreaterThanAndCanceledOrderByEventStartDateTimeDesc(User user, EventType eventType, Date startTime, boolean cancelled, Pageable page);
    Page<Event> findByAppliesToGroupMembershipsUserAndEventTypeAndEventStartDateTimeLessThanAndCanceledOrderByEventStartDateTimeDesc(User user, EventType eventType, Date startTime, boolean cancelled, Pageable page);


    /*
    Queries for analysis, i.e., counting and retrieving how many events took place in a certain period
     */
    List<Event> findByAppliesToGroupAndEventStartDateTimeBetween(Group group, Timestamp start, Timestamp end, Sort sort);
    List<Event> findByAppliesToGroupAndEventTypeAndEventStartDateTimeBetween(Group group, EventType type, Timestamp startDateTime, Timestamp endDateTime);
}
