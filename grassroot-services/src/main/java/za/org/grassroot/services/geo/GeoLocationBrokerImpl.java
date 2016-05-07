package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.geo.UserAndLocalTimeKey;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.repository.PreviousPeriodUserLocationRepository;
import za.org.grassroot.core.repository.UserLocationLogRepository;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.persistence.EntityManager;
import java.time.Instant;
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

		UserLocationLog userLocationLog = new UserLocationLog(time, userUid, new GeoLocation(latitude, longitude));
		logger.info("Logging user location: {}", userLocationLog);
		userLocationLogRepository.save(userLocationLog);
	}

	@Override
	@Transactional
	public void calculatePreviousPeriodUserLocations(LocalDateTime localTime) {
		Objects.requireNonNull(localTime);

		int lastMonthAmount = 2;
		logger.info("calculating user location for period of previous {} months for time {}", lastMonthAmount, localTime);
		LocalDateTime localPeriodStart = localTime.minusMonths(lastMonthAmount);

		Instant intervalStart = convertToSASTInstant(localPeriodStart);
		Instant intervalEnd = convertToSASTInstant(localTime);
		List<UserLocationLog> locationLogs = userLocationLogRepository.findByTimestampBetweenAndTimestampNot(intervalStart, intervalEnd, intervalEnd);

		logger.debug("Deleting all previous period user locations for time: {}", localTime);
		previousPeriodUserLocationRepository.deleteByKeyLocalTime(localTime);

		Map<String, List<GeoLocation>> locationsPerUserUid = locationLogs.stream()
				.collect(Collectors.groupingBy(UserLocationLog::getUserUid,
						Collectors.mapping(UserLocationLog::getLocation, Collectors.toList())));

		logger.debug("Storing {} previous period user locations", locationsPerUserUid.size());

		Set<PreviousPeriodUserLocation> userLocations = new HashSet<>();
		for (Map.Entry<String, List<GeoLocation>> entry : locationsPerUserUid.entrySet()) {
			String userUid = entry.getKey();
			List<GeoLocation> locations = entry.getValue();
			GeoLocation center = GeoLocationUtils.centralLocation(locations);
			PreviousPeriodUserLocation userLocation = new PreviousPeriodUserLocation(new UserAndLocalTimeKey(userUid, localTime), center);
			userLocations.add(userLocation);
		}

		previousPeriodUserLocationRepository.save(userLocations);
	}

	private Instant convertToSASTInstant(LocalDateTime localDateTime) {
		return localDateTime.atZone(DateTimeUtil.getSAST()).toInstant();
	}

	@Override
	public CenterCalculationResult calculateCenter(Set<String> userUids, LocalDateTime time) {
		Objects.requireNonNull(userUids);
		Objects.requireNonNull(time);

		logger.info("calculating geo center from user locations: number of users={}, time={}", userUids.size(), time);

		LocalDateTime previousPeriodLocationLocalTime = findFirstLargetLocalTimeInPreviousPeriodLocations(time);
		List<PreviousPeriodUserLocation> previousPeriodLocations = previousPeriodUserLocationRepository.findByKeyLocalTimeAndKeyUserUidIn(previousPeriodLocationLocalTime, userUids);

		List<GeoLocation> locations = previousPeriodLocations.stream().map(PreviousPeriodUserLocation::getLocation).collect(Collectors.toList());

		int userCount = locations.size(); // number of records is same as user count, since a record is per user-time key
		GeoLocation center = GeoLocationUtils.centralLocation(locations);
		return new CenterCalculationResult(userCount, center);
	}

	private LocalDateTime findFirstLargetLocalTimeInPreviousPeriodLocations(LocalDateTime time) {
		List list = entityManager.createQuery("select l.key.localTime from PreviousPeriodUserLocation l where l.key.locaTime > :time order by l.key.localTime desc")
				.setParameter("time", time)
				.setMaxResults(1)
				.getResultList();
		if (list.isEmpty()) {
			throw new IllegalStateException("There are no previous period locations calculated for times after " + time);
		}
		return (LocalDateTime) list.get(0);
	}
}
