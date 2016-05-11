package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.GroupLocation;

public interface GroupLocationRepository extends JpaRepository<GroupLocation, Long> {
}
