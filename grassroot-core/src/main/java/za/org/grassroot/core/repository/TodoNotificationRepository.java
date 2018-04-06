package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.notification.TodoNotification;

public interface TodoNotificationRepository extends JpaRepository<TodoNotification, Long>, JpaSpecificationExecutor<TodoNotification> {
}
