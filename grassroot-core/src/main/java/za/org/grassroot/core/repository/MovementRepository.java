package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import za.org.grassroot.core.domain.movement.Movement;

public interface MovementRepository extends JpaRepository<Movement, Long>, JpaSpecificationExecutor<Movement> {

    Movement findOneByUid(String movementUid);

}
