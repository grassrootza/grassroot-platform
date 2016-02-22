package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserLogType;

import java.sql.Timestamp;
import java.util.List;

/**
 * Created by luke on 2016/02/22.
 */
public interface UserLogRepository extends JpaRepository<UserLog, Long> {

    List<UserLog> findByUserId(Long userId);

    List<UserLog> findByUserLogType(UserLogType userLogType);

    List<UserLog> findByUserIdAndCreatedDateTimeBetween(Long UserId, Timestamp start, Timestamp end, Sort sort);

    List<UserLog> findByUserLogTypeAndCreatedDateTimeBetween(UserLogType userLogType, Timestamp start, Timestamp end, Sort sort);

}
