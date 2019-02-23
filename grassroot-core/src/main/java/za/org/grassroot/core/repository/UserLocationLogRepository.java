package za.org.grassroot.core.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.geo.UserLocationLog;

import java.time.Instant;
import java.util.List;

public interface UserLocationLogRepository extends JpaRepository<UserLocationLog, String>, JpaSpecificationExecutor<UserLocationLog> {

	// both boudnaries are inclusive when using 'between'
	List<UserLocationLog> findByTimestampBetween(Instant start, Instant end);

	// note: just modify this to match above when modify start/end inclusion
	List<UserLocationLog> findByUserUidAndTimestampBetweenAndTimestampNot(String userUid, Instant start, Instant end, Instant endAgain);

	UserLocationLog findFirstByUserUidAndTimestampAfterOrderByTimestampDesc(String userUid, Instant timestamp);

	List<UserLocationLog> findByUserUidOrderByTimestampDesc(String userUid);

	Page<UserLocationLog> findByMunicipalityIdIsNullAndTimestampAfter(Instant cutoffTime, Pageable pageRequest);

	UserLocationLog findFirstByUserUidAndMunicipalityIdIsNotNullOrderByTimestampDesc(String userUid);
}