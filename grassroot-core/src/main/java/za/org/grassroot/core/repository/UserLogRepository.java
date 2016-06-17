package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;

import java.time.Instant;
import java.util.List;

/**
 * Created by luke on 2016/02/22.
 */
public interface UserLogRepository extends JpaRepository<UserLog, Long> {

    List<UserLog> findByUserUid(String userId);

    List<UserLog> findByUserLogType(UserLogType userLogType);

    List<UserLog> findByUserUidAndCreationTimeBetween(String UserUid, Instant start, Instant end, Sort sort);

    @Transactional(readOnly = true)
    int countByUserUidAndUserLogTypeAndUserInterfaceAndCreationTimeBetween(String userUid, UserLogType userLogType,
                                                                           UserInterfaceType interfaceType, Instant start, Instant end);

    @Transactional(readOnly = true)
    int countByUserUidAndUserLogTypeAndDescription(String userUid, UserLogType userLogType, String description);
}
