package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.LogBookLog;

import java.util.List;

/**
 * Created by aakilomar on 12/5/15.
 */
public interface LogBookLogRepository extends JpaRepository<LogBookLog, Long> {

    List<LogBookLog> findAllByLogBookId(Long logBookId);

}
