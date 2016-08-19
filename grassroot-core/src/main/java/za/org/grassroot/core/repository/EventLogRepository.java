package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    EventLog findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(Event event, EventLogType eventLogType);

    EventLog findByEventAndUserAndEventLogType(Event event, User user, EventLogType eventLogType);

    EventLog findOneByUid(String uid);

    /*
    See if the reminder was already sent before sending it
    */
    @Query(value = "SELECT sum(CASE WHEN response = 'YES' THEN 1 ELSE 0 END) AS yes, " +
            "sum(CASE WHEN response = 'NO' THEN 1 ELSE 0 END) AS no, " +
            "sum(CASE WHEN response = 'MAYBE' THEN 1 ELSE 0 END) AS maybe, " +
            "sum(CASE WHEN response = 'INVALID_RESPONSE' THEN 1 ELSE 0 END) AS invalid, " +
            "(select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers " +
            "FROM event_log el, group_user_membership gu " +
            "WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 'RSVP'",nativeQuery = true)
    List<Object[]> rsvpTotalsForEventAndGroup(Long eventId, Long groupId);

    @Query(value = "SELECT sum(CASE WHEN response = 'YES' THEN 1 ELSE 0 END) AS yes, " +
            "sum(CASE WHEN response = 'NO' THEN 1 ELSE 0 END) AS no, " +
            "sum(CASE WHEN response = 'MAYBE' THEN 1 ELSE 0 END) AS maybe, " +
            "sum(CASE WHEN response = 'INVALID_RESPONSE' THEN 1 ELSE 0 END) AS invalid, " +
            "(select count(*) from group_user_membership gu where gu.group_id = ?2) as numberofusers " +
            "FROM event_log el, group_user_membership gu " +
            "WHERE el.event_id = ?1 AND gu.group_id = ?2 AND el.user_id = gu.user_id AND el.event_log_type = 'RSVP'",nativeQuery = true)
    List<Object[]> voteTotalsForEventAndGroup(Long eventId, Long groupId);
}
