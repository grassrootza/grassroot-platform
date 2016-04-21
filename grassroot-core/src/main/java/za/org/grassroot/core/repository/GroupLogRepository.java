package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.enums.GroupLogType;

import java.time.Instant;
import java.util.List;

public interface GroupLogRepository extends JpaRepository<GroupLog, Long> {

    /*
	Find all the group logs for a particular group, with a filter by type, and different sorts
     */

	List<GroupLog> findByGroupId(Long groupId);

	GroupLog findFirstByGroupIdOrderByCreatedDateTimeDesc(Long groupId);

    List<GroupLog> findByGroupIdAndCreatedDateTimeBetween(Long groupId, Instant startDateTime, Instant endDateTime, Sort sort);

    List<GroupLog> findByGroupIdAndGroupLogTypeAndCreatedDateTimeBetween(Long groupId, GroupLogType type, Instant start, Instant end);
}
