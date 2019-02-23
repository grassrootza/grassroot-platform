package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.TaskLocation;

public interface TaskLocationRepository extends JpaRepository<TaskLocation, Long> {
}
