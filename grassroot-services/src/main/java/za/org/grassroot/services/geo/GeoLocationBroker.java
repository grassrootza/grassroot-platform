package za.org.grassroot.services.geo;

import java.security.cert.CertPathValidatorResult;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Set;

public interface GeoLocationBroker {
	void logUserLocation(String userUid, double latitude, double longitude, Instant time);

	void calculatePreviousPeriodUserLocations(LocalDateTime localIntervalEnd);

	CenterCalculationResult calculateCenter(Set<String> userUids, LocalDateTime time);
}
