package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.NotificationTemplate;
import za.org.grassroot.core.domain.NotificationTriggerType;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Integer> {

    NotificationTemplate findTopByGroupAndTriggerTypeAndActiveTrue(Group group, NotificationTriggerType triggerType);

}
