package za.org.grassroot.core.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface PreviousPeriodUserLocationRepository extends JpaRepository<PreviousPeriodUserLocation, String> {
	void deleteByKeyLocalDate(LocalDate localDate);

	@Transactional(readOnly = true)
	List<PreviousPeriodUserLocation> findByKeyLocalDateAndKeyUserUidIn(LocalDate localDate, Set<String> userUids);

	@Transactional(readOnly = true)
	List<PreviousPeriodUserLocation> findByKeyUserUidIn(Set<String> userUids);
}
