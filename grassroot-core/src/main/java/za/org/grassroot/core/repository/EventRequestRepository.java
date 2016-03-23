package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.EventRequest;
import za.org.grassroot.core.domain.Group;

public interface EventRequestRepository extends JpaRepository<EventRequest, Long> {
	EventRequest findOneByUid(String uid);
}
