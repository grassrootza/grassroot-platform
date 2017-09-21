package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.task.EventRequest;

public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {
	EventRequest findOneByUid(String uid);
}
