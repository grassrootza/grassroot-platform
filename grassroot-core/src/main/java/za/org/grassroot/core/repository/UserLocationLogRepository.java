package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.UserLocationLog;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;

public interface UserLocationLogRepository extends JpaRepository<UserLocationLog, String> {
	// both boudnaries are inclusive when using 'between'
	List<UserLocationLog> findByTimestampBetween(Instant start, Instant end);

	// note: just modify this to match above when modify start/end inclusion
	List<UserLocationLog> findByUserUidAndTimestampBetweenAndTimestampNot(String userUid, Instant start, Instant end, Instant endAgain);

	UserLocationLog findFirstByTimestampBeforeOrderByTimestampDesc(Instant timestamp);

	List<UserLocationLog> findByUserUidOrderByTimestampDesc(String userUid);

	@Transactional
	@Query(value = "SELECT NEW za.org.grassroot.core.domain.geo.UserLocationLog(" +
            " u.timestamp" +
            ",u.userUid" +
            ",u.location" +
            ",u.locationSource" +
            ")" +
            " FROM UserLocationLog u" +
			" WHERE u.uid=:user" +
			" AND u.timestamp BETWEEN :interval AND NOW()")
	UserLocationLog findByUserUidAndTimestampBetweenNowAndIntervalGiven(@Param("user") String user,@Param("interval") Instant interval);

	//List<UserLocationLog> findByUserUid(String userUid);
}
