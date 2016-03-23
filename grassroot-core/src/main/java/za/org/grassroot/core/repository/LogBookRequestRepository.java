package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.LogBookRequest;

public interface LogBookRequestRepository extends JpaRepository<LogBookRequest, Long> {
	LogBookRequest findOneByUid(String uid);
}
