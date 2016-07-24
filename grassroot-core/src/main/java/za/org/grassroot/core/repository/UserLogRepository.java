package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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

    /*
   Some counting and retrieving functions for session analyzing (doing a count on the distinct isn't reliable, so just count the list)
	*/
    @Query("SELECT distinct ul.userUid FROM UserLog ul WHERE " +
            "ul.creationTime BETWEEN ?1 AND ?2 " +
            "AND ul.userLogType = ?3 " +
            "GROUP BY ul.userUid " +
            "HAVING (count(ul) BETWEEN ?4 AND ?5)")
    List<String> fetchUserUidsHavingUserLogTypeCountBetween(Instant start, Instant end, UserLogType userLogType, long low, long high);

    /*
    Next one is a little bit of a hack, as can't nest aggregate functions and JPQL doesn't have 'limit', but won't be used often
     */
    @Query("SELECT count(ul) as countLog from UserLog ul WHERE " +
            "ul.creationTime BETWEEN ?1 AND ?2 " +
            "AND ul.userLogType = ?3 " +
            "GROUP BY ul.userUid " +
            "ORDER BY countLog DESC")
    List<Long> getMaxNumberLogsInInterval(Instant start, Instant end, UserLogType userLogType);

}
