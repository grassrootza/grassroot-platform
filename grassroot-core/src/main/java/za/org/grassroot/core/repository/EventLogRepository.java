package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.CrudRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventLogType;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long> {

    List<EventLog> findByEventLogTypeAndEventAndUser(EventLogType eventLogType, Event event, User user);

    /*
    See if the notification was already sent before sending it
    */
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventNotification")
    public Boolean notificationSent(Event event, User user);

    /*
See if the reminder was already sent before sending it
*/
    @Query("SELECT CASE WHEN COUNT(e) > 0 THEN 'true' ELSE 'false' END FROM EventLog e WHERE e.event = ?1 and e.user = ?2 and e.eventLogType = za.org.grassroot.core.enums.EventLogType.EventReminder")
    public Boolean reminderSent(Event event, User user);

    List<EventLog> findByEventLogTypeAndEventOrderByIdAsc(EventLogType eventLogType, Event event);
}
