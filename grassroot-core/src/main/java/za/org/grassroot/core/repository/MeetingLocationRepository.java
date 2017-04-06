package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.MeetingLocation;

/**
 * Created by luke on 2017/04/06.
 */
public interface MeetingLocationRepository extends JpaRepository<MeetingLocation, Long> {
}
