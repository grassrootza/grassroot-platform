package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.*;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventLogSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.graph.dto.IncomingAnnotation;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static za.org.grassroot.core.enums.LocationSource.convertFromInterface;

@Service
public class GeoLocationBrokerImpl implements GeoLocationBroker {
	private final Logger logger = LoggerFactory.getLogger(GeoLocationBrokerImpl.class);

	private final static int PRIVATE_LEVEL = -1;
	private final static int PUBLIC_LEVEL = 1;

	private final UserLocationLogRepository userLocationLogRepository;
	private final PreviousPeriodUserLocationRepository previousPeriodUserLocationRepository;
	private final UserRepository userRepository;
	private final GroupRepository groupRepository;
	private final GroupLocationRepository groupLocationRepository;
	private final EventRepository eventRepository;
	private final EventLogRepository eventLogRepository;
	private final TaskLocationRepository taskLocationRepository;
	private final EntityManager entityManager;

	private UssdLocationServicesBroker ussdLocationServicesBroker;
	private GraphBroker graphBroker;

    @Autowired
    public GeoLocationBrokerImpl(UserLocationLogRepository userLocationLogRepository, PreviousPeriodUserLocationRepository previousPeriodUserLocationRepository, UserRepository userRepository, GroupRepository groupRepository, GroupLocationRepository groupLocationRepository, EventRepository eventRepository, EventLogRepository eventLogRepository, TaskLocationRepository taskLocationRepository, EntityManager entityManager) {
        this.userLocationLogRepository = userLocationLogRepository;
        this.previousPeriodUserLocationRepository = previousPeriodUserLocationRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupLocationRepository = groupLocationRepository;
        this.eventRepository = eventRepository;
        this.eventLogRepository = eventLogRepository;
        this.taskLocationRepository = taskLocationRepository;
        this.entityManager = entityManager;
    }

    @Autowired(required = false)
    public void setUssdLocationServicesBroker(UssdLocationServicesBroker ussdLocationServicesBroker) {
        this.ussdLocationServicesBroker = ussdLocationServicesBroker;
    }

	@Autowired(required = false)
	public void setGraphBroker(GraphBroker graphBroker) {
		this.graphBroker = graphBroker;
	}

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

		previousPeriodUserLocationRepository.saveAll(userLocations);
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

			// now update group location annotation in graph
			Map<String, String> groupProperties = new HashMap<>();
			groupProperties.put(IncomingAnnotation.latitude, String.valueOf(result.getCenter().getLatitude()));
			groupProperties.put(IncomingAnnotation.longitude, String.valueOf(result.getCenter().getLongitude()));
			graphBroker.annotateGroup(groupUid, groupProperties, null, false);
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

		List<EventLog> logsWithLocation = eventLogRepository.findAll(Specification.where(EventLogSpecifications.forEvent(event))
				.and(EventLogSpecifications.hasLocation()));

		TaskLocation meetingLocation;
		if (!logsWithLocation.isEmpty()) {
			CenterCalculationResult center = calculateCenter(logsWithLocation);
			float score = (float) 1.0; // since a related log with a GPS is best possible, but may return to this
			meetingLocation = new TaskLocation((Meeting) event, center.getCenter(), score,
					EventType.MEETING, LocationSource.LOGGED_MULTIPLE);
		} else {
			Group parent = event.getParent().getThisOrAncestorGroup();
			GroupLocation parentLocation = fetchGroupLocationWithScoreAbove(parent.getUid(),
					localDate, 0);
			if (parentLocation != null) {
				meetingLocation = new TaskLocation((Meeting) event, parentLocation.getLocation(),
						parentLocation.getScore(), EventType.MEETING, LocationSource.CALCULATED);
			} else {
				logger.debug("No event logs or group with location data for meeting");
				meetingLocation = null;
			}
		}

		if (meetingLocation != null) {
			taskLocationRepository.save(meetingLocation);
		}
    }

    @Override
	@Transactional
    public void calculateMeetingLocationInstant(String eventUid, GeoLocation location, UserInterfaceType coordSourceInterface) {
		Meeting meeting = (Meeting) eventRepository.findOneByUid(eventUid);
		logger.info("Calculating a meeting location ...");
		if (location != null) {
			TaskLocation mtgLocation = new TaskLocation(meeting, location, (float) 1.0, EventType.MEETING,
					convertFromInterface(coordSourceInterface));
			taskLocationRepository.save(mtgLocation);
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

	@Override
	@Transactional(readOnly = true)
	public List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation location, Integer radiusInMetres) throws InvalidParameterException {
		return fetchGroupsNearbyWithLocation(location, radiusInMetres, PUBLIC_LEVEL);
	}

	@Transactional(readOnly = true)
	public List<ObjectLocation> fetchGroupsNearbyWithLocation(GeoLocation location, Integer radiusInMetres, Integer publicOrPrivate) throws InvalidParameterException {
		logger.info("Fetching group locations, around {} ...", location);

		assertRadius(radiusInMetres);
		assertGeolocation(location);

		// Mount restriction
		String restrictionClause = "";
		if (publicOrPrivate == null || publicOrPrivate == PUBLIC_LEVEL) {
			restrictionClause = "g.discoverable = true AND ";
		}
		else if (publicOrPrivate == PRIVATE_LEVEL) {
			restrictionClause = "g.discoverable = false AND ";
		}

		// Mount query
		String query =
				"SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation( " +
						"g.uid, g.groupName, l.location.latitude, l.location.longitude, l.score, 'GROUP', g.description, g.discoverable) " +
						"FROM GroupLocation l " +
						"INNER JOIN l.group g " +
						"WHERE " + restrictionClause +
						" l.localDate <= :date " +
						" AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group) AND " +
						GeoLocationUtils.locationFilterSuffix("l.location");

		logger.debug(query);

		TypedQuery<ObjectLocation> typedQuery = entityManager.createQuery(query, ObjectLocation.class)
				.setParameter("date", LocalDate.now());
		GeoLocationUtils.addLocationParamsToQuery(typedQuery, location, radiusInMetres);

		return typedQuery.getResultList();

	}

	public List<ObjectLocation> fetchMeetingLocationsNearUser(User user, GeoLocation geoLocation, Integer radiusInMetres, GeographicSearchType searchType, String searchTerm) {
		Objects.requireNonNull(user);
		Objects.requireNonNull(searchType);
		assertRadius(radiusInMetres);

		GeoLocation searchCentre = geoLocation == null ? fetchBestGuessUserLocation(user.getUid()) : geoLocation;
		if (searchCentre == null) {
			logger.info("No geo location, exiting entity search");
			// we can't find anything, so just return blank (leave to client to work out best way to get location)
			// should probably throw an exception here in time, but for the moment (mid clean up) it's somewhat dangerous
			return new ArrayList<>();
		}

		final String usersOwnGroups = "m.ancestorGroup IN (SELECT mm.group FROM Membership mm WHERE mm.user = :user) AND ";
		final String publicGroupsNotUser = "m.isPublic = true AND m.ancestorGroup NOT IN (SELECT mm.group FROM Membership mm WHERE mm.user = :user) AND ";

		final String groupRestriction = GeographicSearchType.PUBLIC.equals(searchType) ? publicGroupsNotUser :
				GeographicSearchType.PRIVATE.equals(searchType) ? usersOwnGroups : "";

		logger.info("Group restrictions: {}", groupRestriction);

		String strQuery =
				"SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(m, l) " +
						"FROM TaskLocation l INNER JOIN l.meeting m " +
						"WHERE " + groupRestriction + " m.eventStartDateTime >= :present AND "
						+ GeoLocationUtils.locationFilterSuffix("l.location");

		logger.info("we have a search location, it looks like: {}, and query: {}", searchCentre, strQuery);

		TypedQuery<ObjectLocation> query = entityManager.createQuery(strQuery,ObjectLocation.class)
				.setParameter("present", Instant.now());

		if (!GeographicSearchType.BOTH.equals(searchType)) {
			query.setParameter("user", user);
		}

		GeoLocationUtils.addLocationParamsToQuery(query, geoLocation, radiusInMetres);

		return query.getResultList();
	}

	@Override
	@Transactional(readOnly = true)
	public List<ObjectLocation> fetchGroupsNearby(String userUid, GeoLocation location, Integer radiusInMetres, String filterTerm, GeographicSearchType searchType) throws InvalidParameterException {

		assertGeolocation(location);

		Set<ObjectLocation> objectLocationSet = new HashSet<>();

		if (searchType.toInt() > PRIVATE_LEVEL) {
			objectLocationSet.addAll(fetchPublicGroupsNearbyWithLocation(location, radiusInMetres));
		}

		logger.info("Fetching groups, after public fetch, set size = {}", objectLocationSet.size());

		if (searchType.toInt() < PUBLIC_LEVEL) {
			objectLocationSet.addAll(fetchGroupsNearbyWithLocation(location, radiusInMetres,PRIVATE_LEVEL));
		}

		logger.info("After private fetch, set size = {}", objectLocationSet.size());


		return new ArrayList<>(objectLocationSet);
	}

	@Override
	@Transactional(readOnly = true)
	public GeoLocation fetchBestGuessUserLocation(String userUid) {
		GeoLocation bestGuess = null;

		Instant cutOffAge = Instant.now().minus(30, ChronoUnit.DAYS);
		List<UserLocationLog> locationLogs = userLocationLogRepository.findByUserUidOrderByTimestampDesc(userUid);

		if (!locationLogs.isEmpty()) {
			logger.info("we have {} recent location logs", locationLogs.size());

			Optional<UserLocationLog> bestMatch = locationLogs.stream()
					.filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_PRECISE))
					.findFirst();

			logger.info("found a new precise log?: {}", bestMatch);

			bestGuess = bestMatch.isPresent() ? bestMatch.get().getLocation() : null;

			if (bestGuess == null) {
				Optional<UserLocationLog> nextTry = locationLogs.stream()
						.filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_APPROX)).findFirst();
				bestGuess = nextTry.isPresent() ? nextTry.get().getLocation() : null;
				logger.info("found an approx location? {}", nextTry.isPresent());
			}

			if (bestGuess == null) {
				logger.info("found nothing, using this: {}", locationLogs.get(0));
				bestGuess = locationLogs.get(0).getLocation();
			}

		} else {
			logger.info("no recent location logs, going for previously calculated");
			PreviousPeriodUserLocation avgUserLocation = previousPeriodUserLocationRepository
					.findTopByKeyUserUidOrderByKeyLocalDateDesc(userUid);

			if (avgUserLocation != null) {
				bestGuess = avgUserLocation.getLocation();
			}
		}

		return bestGuess;
	}

	private Predicate<UserLocationLog> ofSourceNewerThan(Instant threshold, LocationSource source) {
		return log -> log.getTimestamp().isAfter(threshold) && log.getLocationSource().equals(source);
	}

	private void assertRadius (Integer radius) throws InvalidParameterException {
		if (radius == null || radius <= 0) {
			throw new InvalidParameterException("Invalid radius object.");
		}
	}

	private void assertGeolocation (GeoLocation location) throws InvalidParameterException {
		if (location == null || !location.isValid()) {
			throw new InvalidParameterException("Invalid GeoLocation object.");
		}
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
