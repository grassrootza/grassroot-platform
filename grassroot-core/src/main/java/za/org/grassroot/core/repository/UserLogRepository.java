package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

/**
 * Created by luke on 2016/02/22.
 */
public interface UserLogRepository extends JpaRepository<UserLog, Long>, JpaSpecificationExecutor<UserLog> {

    @Query("SELECT count(distinct ul.userUid) FROM UserLog  ul WHERE " +
            "ul.userLogType = ?1 AND ul.userInterface = ?2")
    long countDistinctUsersHavingLogOnChannel(UserLogType logType, UserInterfaceType channel);

    void deleteAllByUserUidAndUserLogTypeIn(String userUid, List<UserLogType> userLogTypeList);

    List<UserLog> findByUserUidInAndUserLogType(Collection<String> userUids, UserLogType userLogType);
}
