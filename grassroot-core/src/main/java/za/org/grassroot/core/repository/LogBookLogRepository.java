package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.LogBookLog;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.LogBookLogType;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookLogRepository extends JpaRepository<LogBookLog, Long> {
	LogBookLog findFirstByLogBookAndTypeOrderByCreatedDateTimeDesc(LogBook logBook, LogBookLogType type);

}
