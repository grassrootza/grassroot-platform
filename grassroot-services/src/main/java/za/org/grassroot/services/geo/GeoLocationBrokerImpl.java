package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.geo.UserAndLocalDateKey;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.repository.PreviousPeriodUserLocationRepository;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GeoLocationBrokerImpl implements GeoLocationBroker {
	private final Logger logger = LoggerFactory.getLogger(GeoLocationBrokerImpl.class);

	@Autowired
	private UserLocationLogRepository userLocationLogRepository;

	@Autowired
	private PreviousPeriodUserLocationRepository previousPeriodUserLocationRepository;

	@Autowired
	private EntityManager entityManager;

	@Override
	@Transactional
	public void logUserLocation(String userUid, double latitude, double longitude, Instant time) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(time);

		// WARNING: this can potentially become non-performant, so some kind of table partitioning should be employed,
		// or even better, some kind of specialized db storage for that (not sql db)

		UserLocationLog userLocationLog = new UserLocationLog(time, userUid, new GeoLocation(latitude, longitude));
		logger.info("Logging user location: {}", userLocationLog);
		userLocationLogRepository.save(userLocationLog);
	}

	@Override
	@Transactional
	public void calculatePreviousPeriodUserLocations(LocalDate localDate) {
		Objects.requireNonNull(localDate);

		logger.info("calculating user location for period of one month ending on date {}", localDate);
		LocalDate localPeriodStart = localDate.minusMonths(1);

		Instant intervalStart = convertLocalDateToSASTInstant(localPeriodStart);
		Instant intervalEnd = convertLocalDateToSASTInstant(localDate);
		List<UserLocationLog> locationLogs = userLocationLogRepository.findByTimestampBetweenAndTimestampNot(intervalStart, intervalEnd, intervalEnd);

		logger.debug("Deleting all previous period user locations for date: {}", localDate);
		previousPeriodUserLocationRepository.deleteByKeyLocalDate(localDate);

		Map<String, List<GeoLocation>> locationsPerUserUid = locationLogs.stream()
				.collect(Collectors.groupingBy(UserLocationLog::getUserUid,
						Collectors.mapping(UserLocationLog::getLocation, Collectors.toList())));

		logger.debug("Storing {} previous period user locations", locationsPerUserUid.size());

		Set<PreviousPeriodUserLocation> userLocations = new HashSet<>();
		for (Map.Entry<String, List<GeoLocation>> entry : locationsPerUserUid.entrySet()) {
			String userUid = entry.getKey();
			List<GeoLocation> locations = entry.getValue();
			GeoLocation center = GeoLocationUtils.centralLocation(locations);
			PreviousPeriodUserLocation userLocation = new PreviousPeriodUserLocation(new UserAndLocalDateKey(userUid, localDate), center, locations.size());
			userLocations.add(userLocation);
		}

		previousPeriodUserLocationRepository.save(userLocations);
	}

	private Instant convertLocalDateToSASTInstant(LocalDate localDate) {
		return localDate.atStartOfDay().atZone(DateTimeUtil.getSAST()).toInstant();
	}

	@Override
	public CenterCalculationResult calculateCenter(Set<String> userUids, LocalDate date) {
		Objects.requireNonNull(userUids);
		Objects.requireNonNull(date);

		logger.info("calculating geo center from user locations: number of users={}, local date={}", userUids.size(), date);

		// should we ask for exact date, or be good with accepting future dates if current one does not exist?
//		LocalDate previousPeriodLocationLocalDate = findFirstLocalDateInPreviousPeriodLocationsAfterOrEqualsDate(date);
		LocalDate previousPeriodLocationLocalDate = date; // todo: maybe this is sufficient (to search only for exact date)?
		List<PreviousPeriodUserLocation> previousPeriodLocations = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(previousPeriodLocationLocalDate, userUids);

		List<GeoLocation> locations = previousPeriodLocations.stream().map(PreviousPeriodUserLocation::getLocation).collect(Collectors.toList());

		int userCount = locations.size(); // number of records is same as user count, since a record is per user-time key
		GeoLocation center = GeoLocationUtils.centralLocation(locations);
		return new CenterCalculationResult(userCount, center);
	}

	private LocalDate findFirstLocalDateInPreviousPeriodLocationsAfterOrEqualsDate(LocalDate date) {
		List list = entityManager.createQuery("select l.key.localDate from PreviousPeriodUserLocation l where l.key.localDate >= :date order by l.key.localDate desc")
				.setParameter("date", date)
				.setMaxResults(1) // limit to first only
				.getResultList();
		if (list.isEmpty()) {
			throw new IllegalStateException("There are no previous period locations calculated for dates after " + date);
		}
		return (LocalDate) list.get(0);
	}
}
