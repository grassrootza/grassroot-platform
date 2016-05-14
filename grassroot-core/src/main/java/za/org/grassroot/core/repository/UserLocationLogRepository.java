package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.UserLocationLog;

import java.time.Instant;
import java.util.List;

public interface UserLocationLogRepository extends JpaRepository<UserLocationLog, String> {
	// we need to specifiy that end is not inclusive
	List<UserLocationLog> findByTimestampBetweenAndTimestampNot(Instant start, Instant end, Instant endAgain);

	// note: just modify this to match above when modify start/end inclusion
	List<UserLocationLog> findByUserUidAndTimestampBetweenAndTimestampNot(String userUid, Instant start, Instant end, Instant endAgain);

	UserLocationLog findFirstByTimestampBeforeOrderByTimestampDesc(Instant timestamp);
}
