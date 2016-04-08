package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByEventLogTypeAndEventAndUser(EventLogType eventLogType, Event event, User user);

    List<EventLog> findByEventAndEventLogTypeNot(Event event, EventLogType eventLogType);

    EventLog findByEventAndUserAndEventLogType(Event event, User user, EventLogType eventLogType);

    /*
    See if the notification was already sent before sending it
    */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventNotification")
    public Boolean notificationSent(Event event, User user);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventChange and e.message = ?3")
    public Boolean changeNotificationSent(Event event, User user, String message);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventCancelled")
    public Boolean cancelNotificationSent(Event event, User user);

    /*
    See if the reminder was already sent before sending it
    */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventReminder")
    public Boolean reminderSent(Event event, User user);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventResult")
    public Boolean voteResultSent(Event event, User user);

    @Query(value = "SELECT sum(CASE WHEN message = 'Yes' THEN 1 ELSE 0 END) AS yes, sum(CASE WHEN message = 'No' THEN 1 ELSE 0 END) AS no, sum(CASE WHEN message = 'Maybe' THEN 1 ELSE 0 END) AS maybe, sum(CASE WHEN message = 'Invalid RSVP' THEN 1 ELSE 0 END) AS invalid, (select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers FROM event_log el, group_user_membership gu WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 5",nativeQuery = true)
    public List<Object[]> rsvpTotalsForEventAndGroup(Long eventId, Long groupId);
    /*
    check if user rsvp.no for event, so we do not send more messages
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP and e.message = 'No'")
    public Boolean rsvpNoForEvent(Event event, User user);

    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventRSVP")
    public Boolean userRsvpForEvent(Event event, User user);

    @Query(value = "SELECT sum(CASE WHEN message = 'Yes' THEN 1 ELSE 0 END) AS yes, sum(CASE WHEN message = 'No' THEN 1 ELSE 0 END) AS no, sum(CASE WHEN message = 'Maybe' THEN 1 ELSE 0 END) AS maybe, sum(CASE WHEN message = 'Invalid RSVP' THEN 1 ELSE 0 END) AS invalid,(select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers FROM event_log el, group_user_membership gu WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 5",nativeQuery = true)
    public List<Object[]> voteTotalsForEventAndGroup(Long eventId, Long groupId);


    /*
    Probably a good idea to write a query that returns all the eventLogs which apply to a given user and for which they have
    not RSVP'd yet ... for now, at a basic level, just calling the whole list, ordered ID descending (most recent first)
     */

    List<EventLog> findByUserOrderByIdDesc(User user);

    @Query(value = "WITH RECURSIVE tree(id, created_date_time, name, group_token_code, token_code_expiry, created_by_user, parent, version, reminderminutes) AS ( SELECT pg.* FROM group_profile pg WHERE pg.id = ?1 UNION ALL SELECT sg.* FROM group_profile sg, tree AS nodes WHERE sg.parent = nodes.id ) SELECT tree.id, tree.name, sum(CASE WHEN message = 'Yes' THEN 1 ELSE 0 END) AS yes, sum(CASE WHEN message = 'No' THEN 1 ELSE 0 END) AS no, (select count(*) from group_user_membership gu2 where gu2.group_id = tree.id) as noofusers FROM tree, event_log el, group_user_membership gu where tree.id = gu.group_id and el.user_id = gu.user_id and el.event_log_type = 5 and el.event_id = ?2 group by tree.id, tree.name order by tree.id", nativeQuery = true)
    public List<Object[]> voteTotalsPerGroupAndSubGroup(Long startingGroupId, Long eventId);

}
