package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.GroupJoinRequestEvent;

public interface GroupJoinRequestEventRepository extends JpaRepository<GroupJoinRequestEvent, Long> {
}
