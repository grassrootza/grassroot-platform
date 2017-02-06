package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.association.AssociationRequestEvent;

public interface AssociationRequestEventRepository extends JpaRepository<AssociationRequestEvent, Long> {
}
