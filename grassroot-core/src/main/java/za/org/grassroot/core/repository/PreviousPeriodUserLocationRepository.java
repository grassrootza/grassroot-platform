package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface PreviousPeriodUserLocationRepository extends JpaRepository<PreviousPeriodUserLocation, String> {
	void deleteByKeyLocalTime(LocalDateTime localTime);

	List<PreviousPeriodUserLocation> findByKeyLocalTimeAndKeyUserUidIn(LocalDateTime localTime, Set<String> userUids);
}
