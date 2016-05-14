package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.*;
import za.org.grassroot.core.repository.*;
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
	private UserRepository userRepository;

	@Autowired
	private GroupRepository groupRepository;

	@Autowired
	private GroupLocationRepository groupLocationRepository;

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

	@Override
	public PreviousPeriodUserLocation fetchUserLocation(String userUid, LocalDate localDate) {
		LocalDate mostRecentRecordedAverage = findFirstDateWithAvgLocationForUserBefore(localDate, userUid);
		if (mostRecentRecordedAverage == null) {
			return null;
		} else {
			return previousPeriodUserLocationRepository.
					findByKeyLocalDateAndKeyUserUidIn(localDate, Collections.singleton(userUid)).get(0);
		}
	}

	@Override
	public List<User> fetchUsersWithRecordedAverageLocations(LocalDate localDate) {
		return findUsersWithAverageLocationBefore(localDate);
	}

	@Override
	@Transactional(readOnly = true)
	public GroupLocation fetchGroupLocationWithScoreAbove(String groupUid, LocalDate localDate, float score) {
		Group group = groupRepository.findOneByUid(groupUid);
		LocalDate mostRecentMatchingDate = findFirstDateWithGroupLocationHavingScoreAbove(localDate, group, score);
		if (mostRecentMatchingDate == null) {
			return null;
		} else {
			return groupLocationRepository.findOneByGroupAndLocalDate(group, mostRecentMatchingDate);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public List<double[]> fetchUserLatitudeLongitudeInAvgPeriod(String userUid, LocalDate localDate) {

		List<double[]> returnList = new ArrayList<>();
		PreviousPeriodUserLocation avg = fetchUserLocation(userUid, localDate);
		returnList.add(new double[]{ avg.getLocation().getLatitude(), avg.getLocation().getLongitude() });

		LocalDate localPeriodStart = localDate.minusMonths(1);

		Instant intervalStart = convertLocalDateToSASTInstant(localPeriodStart);
		Instant intervalEnd = convertLocalDateToSASTInstant(localDate);

		List<UserLocationLog> logs = userLocationLogRepository.
				findByUserUidAndTimestampBetweenAndTimestampNot(userUid, intervalStart, intervalEnd, intervalEnd);

		for (UserLocationLog log : logs) {
			returnList.add(new double[]{ log.getLocation().getLatitude(), log.getLocation().getLongitude() });
		}

		return returnList;
	}

	private LocalDate findFirstDateWithAvgLocationBefore(LocalDate date) {

		List list = entityManager.createQuery("select l.key.localDate from PreviousPeriodUserLocation l where l.key.localDate <= :date order by l.key.localDate desc")
				.setParameter("date", date)
				.setMaxResults(1) // limit to first only
				.getResultList();

		return (list.isEmpty()) ? null : (LocalDate) list.get(0);
	}

	private LocalDate findFirstDateWithAvgLocationForUserBefore(LocalDate date, String userUid) {
		List list = entityManager.createQuery("select l.key.localDate from PreviousPeriodUserLocation l " +
													  "where l.key.localDate <= :date and l.key.userUid = :user_uid " +
													  "order by l.key.localDate desc")
				.setParameter("date", date)
				.setParameter("user_uid", userUid)
				.setMaxResults(1) // limit to first only
				.getResultList();

		return (list.isEmpty()) ? null : (LocalDate) list.get(0);
	}

	private LocalDate findFirstDateWithGroupLocationHavingScoreAbove(LocalDate date, Group group, float score) {

		List list = entityManager.createQuery("select l.localDate from GroupLocation l " +
													  "where l.localDate <= :date and l.group = :group_sought and l.score >= :score " +
													  "order by l.localDate desc")
				.setParameter("date", date)
				.setParameter("group_sought", group)
				.setParameter("score", score)
				.getResultList();

		return (list.isEmpty()) ? null : (LocalDate) list.get(0);
	}

	private List<User> findUsersWithAverageLocationBefore(LocalDate date) {
		List list = entityManager.createQuery("select l.key.userUid from PreviousPeriodUserLocation l where " +
															 "l.key.localDate <= :date")
				.setParameter("date", date).getResultList();
		if (list.isEmpty()) {
			return new ArrayList<>();
		} else {
			Set<String> uids = new HashSet<>(list);
			return userRepository.findByUidIn(uids);
		}
	}
}
