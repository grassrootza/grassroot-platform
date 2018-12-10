package za.org.grassroot.core.repository;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.enums.GroupLogType;

import java.time.Instant;
import java.util.Collection;
import java.util.List;

public interface GroupLogRepository extends JpaRepository<GroupLog, Long>, JpaSpecificationExecutor<GroupLog> {

	List<GroupLog> findByGroup(Group group);

	GroupLog findFirstByGroupOrderByCreatedDateTimeDesc(Group group);

    List<GroupLog> findByGroupLogTypeIn(Collection<GroupLogType> types, Pageable pageable);
}
