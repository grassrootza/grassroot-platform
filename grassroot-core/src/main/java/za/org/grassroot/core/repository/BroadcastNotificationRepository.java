package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.notification.BroadcastNotification;

public interface BroadcastNotificationRepository extends JpaRepository<BroadcastNotification, Long>, JpaSpecificationExecutor<BroadcastNotification> {
}
