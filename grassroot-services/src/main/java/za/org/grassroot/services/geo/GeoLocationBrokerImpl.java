package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.*;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;

import javax.persistence.EntityManager;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.enums.LocationSource.convertFromInterface;

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
	private EventRepository eventRepository;

	@Autowired
	private EventLogRepository eventLogRepository;

	@Autowired
	private MeetingLocationRepository meetingLocationRepository;

	@Autowired(required = false)
	private UssdLocationServicesBroker ussdLocationServicesBroker;

	@Autowired
	private RestTemplate restTemplate;

	@Autowired
	private EntityManager entityManager;

	@Override
	@Transactional
	public void logUserLocation(String userUid, double latitude, double longitude, Instant time, UserInterfaceType interfaceType) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(time);

		// WARNING: this can potentially become non-performant, so some kind of table partitioning should be employed,
		// or even better, some kind of specialized db storage for that (not sql db)

		UserLocationLog userLocationLog = new UserLocationLog(time, userUid, new GeoLocation(latitude, longitude),
				LocationSource.convertFromInterface(interfaceType));
		userLocationLogRepository.save(userLocationLog);
	}

	/*
	Logic sequence, for the moment (really need to overhaul most of this):
	* (1) First, look for an address of the user, via safety broker, that has a location
	* (2) If not, look for a stored / average user location
	* (3) If not that, look for a group / event with a stored user location
	* On each of the above, look for an address / address log in the vicinity, and return it
	* Otherwise, call the geolocator, then store the address so we don't need it again in future
	 */
    @Override
	@Transactional(readOnly = true)
    public String describeUserLocation(String userUid) {

		PreviousPeriodUserLocation avgLocation = fetchUserLocation(userUid);
		if (avgLocation != null) {

		}
		return null;
    }

    @Async
    @Override
	@Transactional
    public void logUserUssdPermission(String userUid, String entityToUpdateUid,
									  JpaEntityType entityType, boolean singleTrackPermission) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(entityToUpdateUid);

        logger.info("Logging USSD permission to use location, should be off main thread");
        boolean priorAllowed = ussdLocationServicesBroker.isUssdLocationLookupAllowed(userUid);
        boolean lookupAllowed = priorAllowed ||
				ussdLocationServicesBroker.addUssdLocationLookupAllowed(userUid, UserInterfaceType.USSD);

        if (lookupAllowed) {
			GeoLocation userLocation = ussdLocationServicesBroker.getUssdLocationForUser(userUid);
			logger.info("Retrieved a user location: {}", userLocation);
			switch (entityType) {
				case MEETING:
					calculateMeetingLocationInstant(entityToUpdateUid, userLocation, UserInterfaceType.USSD);
					break;
				case TODO:
					calculateTodoLocationInstant(entityToUpdateUid, userLocation, UserInterfaceType.USSD);
					break;
				case GROUP:
					calculateGroupLocationInstant(entityToUpdateUid, userLocation, UserInterfaceType.USSD);
					break;
				case SAFETY:
					// todo : add this
					break;
				default:
					logger.info("Location attempted for an entity that should not have a location attached");
			}

			if (!priorAllowed && singleTrackPermission) {
				ussdLocationServicesBroker.removeUssdLocationLookup(userUid, UserInterfaceType.SYSTEM);
			}
		}
    }

    @Override
	@Transactional
	public void calculatePreviousPeriodUserLocations(LocalDate localDate) {
		Objects.requireNonNull(localDate);

		logger.info("calculating user locations for period of one month ending on date {} (inclusive)", localDate);
		int periodDurationInMonths = 1;
		Instant intervalStart = convertStartOfDayToSASTInstant(localDate.plusDays(1).minusMonths(periodDurationInMonths));
		Instant intervalEnd = convertStartOfDayToSASTInstant(localDate.plusDays(1)); // since we want period date end to be inclusive...
		List<UserLocationLog> locationLogs = userLocationLogRepository.findByTimestampBetween(intervalStart, intervalEnd);

		logger.debug("Deleting all previous period user locations for date: {}", localDate);
		previousPeriodUserLocationRepository.deleteByKeyLocalDate(localDate);

		Map<String, List<GeoLocation>> locationsPerUserUid = locationLogs.stream()
				.collect(Collectors.groupingBy(UserLocationLog::getUserUid,
						Collectors.mapping(UserLocationLog::getLocation, Collectors.toList())));

		logger.info("Storing {} previous period user locations", locationsPerUserUid.size());

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

	private Instant convertStartOfDayToSASTInstant(LocalDate date) {
		return date.atStartOfDay().atZone(DateTimeUtil.getSAST()).toInstant();
	}

	@Override
	@Transactional
	public void calculateGroupLocation(String groupUid, LocalDate localDate) {
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(localDate);

		Group group = groupRepository.findOneByUid(groupUid);

		// todo: look for direct logged locations and rank them higher

		// delete so we can recalculate
		groupLocationRepository.deleteByGroupAndLocalDate(group, localDate);

		Set<String> memberUids = group.getMembers().stream().map(User::getUid).collect(Collectors.toSet());
		CenterCalculationResult result = calculateCenter(memberUids, localDate);
		if (result.isDefined()) {
			// for now, score is simply ratio of found member locations to total member count
			logger.info("in group location, saving center result: {}", result);
			float score = result.getEntityCount() / (float) memberUids.size();
			GroupLocation groupLocation = new GroupLocation(group, localDate, result.getCenter(), score, LocationSource.CALCULATED);
			groupLocationRepository.save(groupLocation);
		} else {
			logger.debug("No member location data found for group {} for local date {}", group, localDate);
		}
	}

	@Override
	@Transactional
	public void calculateGroupLocationInstant(String groupUid, GeoLocation location, UserInterfaceType coordSourceInterface) {
		Objects.requireNonNull(groupUid);
		Objects.requireNonNull(location);

		Group group = groupRepository.findOneByUid(groupUid);

		GroupLocation groupLocation = new GroupLocation(group, LocalDate.now(), location, (float) 1.0,
				convertFromInterface(coordSourceInterface));
		groupLocationRepository.save(groupLocation);
	}

	@Override
	@Transactional
    public void calculateMeetingLocationScheduled(String eventUid, LocalDate localDate) {
        Event event = eventRepository.findOneByUid(eventUid);
		if (!EventType.MEETING.equals(event.getEventType())) {
			throw new IllegalArgumentException("Cannot calculate a location for a vote");
		}

		List<EventLog> logsWithLocation = eventLogRepository.findAll(
				Specifications.where(EventLogSpecifications.forEvent(event))
				.and(EventLogSpecifications.hasLocation()));

		MeetingLocation meetingLocation;
		if (logsWithLocation != null && !logsWithLocation.isEmpty()) {
			CenterCalculationResult center = calculateCenter(logsWithLocation);
			float score = (float) 1.0; // since a related log with a GPS is best possible, but may return to this
			meetingLocation = new MeetingLocation((Meeting) event, center.getCenter(), score,
					EventType.MEETING, LocationSource.LOGGED_MULTIPLE);
		} else {
			Group parent = event.getParent().getThisOrAncestorGroup();
			GroupLocation parentLocation = fetchGroupLocationWithScoreAbove(parent.getUid(),
					localDate, 0);
			if (parentLocation != null) {
				meetingLocation = new MeetingLocation((Meeting) event, parentLocation.getLocation(),
						parentLocation.getScore(), EventType.MEETING, LocationSource.CALCULATED);
			} else {
				logger.debug("No event logs or group with location data for meeting");
				meetingLocation = null;
			}
		}

		if (meetingLocation != null) {
			meetingLocationRepository.save(meetingLocation);
		}
    }

    @Override
	@Transactional
    public void calculateMeetingLocationInstant(String eventUid, GeoLocation location, UserInterfaceType coordSourceInterface) {
		Meeting meeting = (Meeting) eventRepository.findOneByUid(eventUid);
		logger.info("Calculating a meeting location ...");
		if (location != null) {
			MeetingLocation mtgLocation = new MeetingLocation(meeting, location, (float) 1.0, EventType.MEETING,
					convertFromInterface(coordSourceInterface));
			meetingLocationRepository.save(mtgLocation);
		} else {
			LocalDate inLastMonth = LocalDate.now().minusMonths(1L);
			Group ancestor = meeting.getAncestorGroup();
			if (groupLocationRepository.countByGroupAndLocalDateGreaterThan(ancestor, inLastMonth) == 0) {
				logger.info("from meeting, triggering a group location calculation ...");
				calculateGroupLocation(ancestor.getUid(), LocalDate.now());
			}
			calculateMeetingLocationScheduled(eventUid, LocalDate.now());
		}
    }

    @Override
    public void calculateTodoLocationScheduled(String todoUid, LocalDate localDate) {

    }

	@Override
	public void calculateTodoLocationInstant(String todoUid, GeoLocation location, UserInterfaceType coordSourceInterface) {

	}

	@Override
	@Transactional
	public CenterCalculationResult calculateCenter(Set<String> userUids, LocalDate date) {
		Objects.requireNonNull(userUids);
		Objects.requireNonNull(date);

		logger.info("calculating geo center from user locations: number of users={}, local date={}", userUids.size(), date);

		// should we ask for exact date, or be good with accepting future dates if current one does not exist?
//		LocalDate previousPeriodLocationLocalDate = findFirstLocalDateInPreviousPeriodLocationsAfterOrEqualsDate(date);
		List<PreviousPeriodUserLocation> previousPeriodLocations = previousPeriodUserLocationRepository.findByKeyLocalDateAndKeyUserUidIn(date, userUids);

		// note: number of records is same as user count, since a record is per user-time key
		return calculateCenter(previousPeriodLocations);
	}

	private CenterCalculationResult calculateCenter(List<? extends LocationHolder> locationHolders) {
		List<GeoLocation> locations = locationHolders.stream().map(LocationHolder::getLocation)
				.collect(Collectors.toList());
		return new CenterCalculationResult(locations.size(), GeoLocationUtils.centralLocation(locations));
	}

	@Override
	public PreviousPeriodUserLocation fetchUserLocation(String userUid, LocalDate localDate) {
		LocalDate mostRecentRecordedAverage = findFirstDateWithAvgLocationForUserBefore(localDate, userUid);
		if (mostRecentRecordedAverage == null) {
			return null;
		} else {
			List<PreviousPeriodUserLocation> priorLocations = previousPeriodUserLocationRepository.
					findByKeyLocalDateAndKeyUserUidIn(localDate, Collections.singleton(userUid));
			if (priorLocations != null && !priorLocations.isEmpty()) {
				return priorLocations.get(0);
			} else {
				return null;
			}
		}
	}

	@Override
	public PreviousPeriodUserLocation fetchUserLocation(String userUid) {
		LocalDate mostRecentRecordedAverage = findFirstDateWithAvgLocationForUserBefore(LocalDate.now(), userUid);
		if (mostRecentRecordedAverage == null) {
			return null;
		} else {
			List<PreviousPeriodUserLocation> priorLocations = previousPeriodUserLocationRepository.
					findByKeyUserUidIn(Collections.singleton(userUid));
			if (priorLocations != null && !priorLocations.isEmpty()) {
				return priorLocations.get(0);
			} else {
				return null;
			}
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
	public List<GroupLocation> fetchGroupLocationsWithScoreAbove(Set<Group> groups, LocalDate localDate, float score) {
		return groupLocationRepository.findByGroupInAndLocalDateAndScoreGreaterThan(groups, localDate, score);
	}


	@Override
	public List<Group> fetchGroupsWithRecordedAverageLocations(){
		return groupLocationRepository.findAllGroupsWithLocationData();

	}

	@Override
	public List<Group> fetchGroupsWithRecordedLocationsFromSet(Set<Group> referenceSet) {
		logger.info("looking for groups in this set : " + referenceSet.toString());
		return referenceSet.isEmpty() ? new ArrayList<>() :
				groupLocationRepository.findAllGroupsWithLocationDataInReferenceSet(referenceSet);
	}

	@Override
	@Transactional(readOnly = true)
	public List<double[]> fetchUserLatitudeLongitudeInAvgPeriod(String userUid, LocalDate localDate) {

		List<double[]> returnList = new ArrayList<>();
		PreviousPeriodUserLocation avg = fetchUserLocation(userUid, localDate);
		returnList.add(new double[]{ avg.getLocation().getLatitude(), avg.getLocation().getLongitude() });

		LocalDate localPeriodStart = localDate.minusMonths(1);

		Instant intervalStart = convertStartOfDayToSASTInstant(localPeriodStart);
		Instant intervalEnd = convertStartOfDayToSASTInstant(localDate);

		List<UserLocationLog> logs = userLocationLogRepository.
				findByUserUidAndTimestampBetweenAndTimestampNot(userUid, intervalStart, intervalEnd, intervalEnd);

		for (UserLocationLog log : logs) {
			returnList.add(new double[]{ log.getLocation().getLatitude(), log.getLocation().getLongitude() });
		}

		return returnList;
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
