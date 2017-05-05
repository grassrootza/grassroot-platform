package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.MeetingLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import java.util.Collection;
import java.util.List;

public interface MeetingLocationRepository extends JpaRepository<MeetingLocation, Long> {
}
