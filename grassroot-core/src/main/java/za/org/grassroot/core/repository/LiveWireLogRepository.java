package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.livewire.LiveWireLog;
import za.org.grassroot.core.enums.LiveWireLogType;

import java.util.List;

/**
 * Created by luke on 2017/05/17.
 */
public interface LiveWireLogRepository extends JpaRepository<LiveWireLog, Long>, JpaSpecificationExecutor<LiveWireLog> {

    List<LiveWireLog> findByType(LiveWireLogType type, Pageable pageable);

    @Query(value = "SELECT COUNT(l.type) FROM live_wire_log l " +
            "WHERE l.type='ALERT_BLOCKED' " +
            "AND l.creation_time < (CURRENT_TIMESTAMP - INTERVAL '1 DAY') " +
            "AND l.user_acting_id=?1",nativeQuery = true)
    int countNumberOfTimesUserAlertWasBlocked(User user);
}
