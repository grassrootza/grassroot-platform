package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.TodoRequest;

public interface TodoRequestRepository extends JpaRepository<TodoRequest, Long> {
	TodoRequest findOneByUid(String uid);
}
