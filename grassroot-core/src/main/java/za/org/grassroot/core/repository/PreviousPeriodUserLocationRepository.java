package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface PreviousPeriodUserLocationRepository extends JpaRepository<PreviousPeriodUserLocation, String> {
	void deleteByKeyLocalDate(LocalDate localDate);

	List<PreviousPeriodUserLocation> findByKeyLocalDateAndKeyUserUidIn(LocalDate localDate, Set<String> userUids);
}
