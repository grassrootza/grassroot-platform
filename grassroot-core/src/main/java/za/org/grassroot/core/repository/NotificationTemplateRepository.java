package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Integer> {

    NotificationTemplate findTopByGroupAndActiveTrue(Group group);

}
