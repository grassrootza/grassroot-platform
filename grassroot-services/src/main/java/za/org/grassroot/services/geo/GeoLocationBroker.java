package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface GeoLocationBroker {
	void logUserLocation(String userUid, double latitude, double longitude, Instant time);

	void calculatePreviousPeriodUserLocations(LocalDate localDate);

	CenterCalculationResult calculateCenter(Set<String> userUids, LocalDate date);

	PreviousPeriodUserLocation fetchUserLocation(String userUid, LocalDate localDate);

	List<User> fetchUsersWithRecordedAverageLocations(LocalDate localDate);

	GroupLocation fetchGroupLocationWithScoreAbove(String groupUid, LocalDate localDate, float score);
}
