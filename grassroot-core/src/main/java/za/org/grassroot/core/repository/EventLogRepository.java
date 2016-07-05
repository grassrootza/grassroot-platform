package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;

import java.util.List;
import java.util.Set;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    EventLog findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(Event event, EventLogType eventLogType);

    EventLog findByEventAndUserAndEventLogType(Event event, User user, EventLogType eventLogType);

    EventLog findOneByUid(String uid);

    /*
    See if the reminder was already sent before sending it
    */
    @Query(value = "SELECT sum(CASE WHEN message = 'Yes' THEN 1 ELSE 0 END) AS yes, sum(CASE WHEN message = 'No' THEN 1 ELSE 0 END) AS no, sum(CASE WHEN message = 'Maybe' THEN 1 ELSE 0 END) AS maybe, sum(CASE WHEN message = 'Invalid RSVP' THEN 1 ELSE 0 END) AS invalid, (select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers FROM event_log el, group_user_membership gu WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 'RSVP'",nativeQuery = true)
    public List<Object[]> rsvpTotalsForEventAndGroup(Long eventId, Long groupId);
    /*
    check if user rsvp.no for event, so we do not send more messages, and yes, so we send thank you
     */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.RSVP")
    public Boolean userRsvpForEvent(Event event, User user);

    @Query(value = "SELECT sum(CASE WHEN message = 'Yes' THEN 1 ELSE 0 END) AS yes, sum(CASE WHEN message = 'No' THEN 1 ELSE 0 END) AS no, sum(CASE WHEN message = 'Maybe' THEN 1 ELSE 0 END) AS maybe, sum(CASE WHEN message = 'Invalid RSVP' THEN 1 ELSE 0 END) AS invalid,(select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers FROM event_log el, group_user_membership gu WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 'RSVP'",nativeQuery = true)
    public List<Object[]> voteTotalsForEventAndGroup(Long eventId, Long groupId);
}
