package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;

import java.util.List;

public interface EventLogRepository extends JpaRepository<EventLog, Long>, JpaSpecificationExecutor<EventLog> {

    EventLog findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(Event event, EventLogType eventLogType);

    EventLog findByEventAndUserAndEventLogType(Event event, User user, EventLogType eventLogType);

    long countByEventAndUserAndEventLogType(Event event, User user, EventLogType eventLogType);

    List<EventLog> findByEventAndEventLogType(Event event, EventLogType type);

    List<EventLog> findByEventLogTypeAndEventType(EventLogType logType, EventType eventType, Pageable pageable);
}
