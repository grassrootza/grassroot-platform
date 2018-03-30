package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.notification.EventNotification;

public interface EventNotificationRepository extends JpaRepository<EventNotification, Long>, JpaSpecificationExecutor<EventNotification> {
}
