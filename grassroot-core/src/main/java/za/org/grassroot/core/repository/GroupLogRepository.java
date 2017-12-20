package za.org.grassroot.core.repository;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.enums.GroupLogType;

import java.time.Instant;
import java.util.List;

public interface GroupLogRepository extends JpaRepository<GroupLog, Long>, JpaSpecificationExecutor<GroupLog> {

    /*
	Find all the group logs for a particular group, with a filter by type, and different sorts
     */

	List<GroupLog> findByGroup(Group group);

	GroupLog findFirstByGroupOrderByCreatedDateTimeDesc(Group group);

    List<GroupLog> findByGroupAndCreatedDateTimeBetween(Group group, Instant startDateTime, Instant endDateTime, Sort sort);

    List<GroupLog> findByGroupAndGroupLogTypeAndCreatedDateTimeBetween(Group group, GroupLogType type, Instant start, Instant end);
}
