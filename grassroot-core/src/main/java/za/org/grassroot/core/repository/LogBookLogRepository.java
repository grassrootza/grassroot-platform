package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.LogBookLog;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookLogRepository extends JpaRepository<LogBookLog, Long> {

	LogBook findOneByUid(String uid);
}
