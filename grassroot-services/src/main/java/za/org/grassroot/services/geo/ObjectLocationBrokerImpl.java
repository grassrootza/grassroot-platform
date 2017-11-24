package za.org.grassroot.services.geo;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.geo.UserLocationLog;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.group.GroupLocationFilter;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.function.Predicate;

@Service
@Slf4j
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {

    private final static int ALL_LEVEL = 0;
    private final static int PRIVATE_LEVEL = -1;
    private final static int PUBLIC_LEVEL = 1;

    private final EntityManager entityManager;
    private final GroupLocationRepository groupLocationRepository;
    private final MeetingLocationRepository meetingLocationRepository;
    private final UserLocationLogRepository userLocationLogRepository;
    private final PreviousPeriodUserLocationRepository avgLocationRepository;

    @Autowired
    public ObjectLocationBrokerImpl(EntityManager entityManager, GroupLocationRepository groupLocationRepository,
                                    MeetingLocationRepository meetingLocationRepository,
                                    UserLocationLogRepository userLocationLogRepository,
                                    PreviousPeriodUserLocationRepository avgLocationRepository,UserRepository userRepository) {
        this.entityManager = entityManager;
        this.groupLocationRepository = groupLocationRepository;
        this.meetingLocationRepository = meetingLocationRepository;
        this.userLocationLogRepository = userLocationLogRepository;
        this.avgLocationRepository = avgLocationRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation location, Integer radiusInMetres) throws InvalidParameterException {
        return fetchGroupsNearbyWithLocation(location, radiusInMetres, PUBLIC_LEVEL);
    }

    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupsNearbyWithLocation(GeoLocation location, Integer radiusInMetres, Integer publicOrPrivate) throws InvalidParameterException {
        log.info("Fetching group locations, around {} ...", location);

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

        log.debug(query);

        TypedQuery<ObjectLocation> typedQuery = entityManager.createQuery(query, ObjectLocation.class)
                .setParameter("date", LocalDate.now());
        GeoLocationUtils.addLocationParamsToQuery(typedQuery, location, radiusInMetres);

        return typedQuery.getResultList();

    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchLocationsWithFilter(GroupLocationFilter filter) {
        List<ObjectLocation> locations = new ArrayList<>();

        Instant earliestDate = filter.getMinimumGroupLifeWeeks() == null ? DateTimeUtil.getEarliestInstant() :
                LocalDate.now().minus(filter.getMinimumGroupLifeWeeks(), ChronoUnit.WEEKS)
                .atStartOfDay().toInstant(ZoneOffset.UTC);

        // note : if need to optimize performance, leave out size counts if nulls passed, instead of check > 0
        List<Group> groupsToInclude = entityManager.createQuery("" +
                "select g from Group g where " +
                "g.createdDateTime >= :createdDateTime and " +
                "size(g.memberships) >= :minMembership and " +
                "(size(g.descendantEvents) + size(g.descendantTodos)) >= :minTasks", Group.class)
                .setParameter("createdDateTime", earliestDate)
                .setParameter("minMembership", filter.getMinimumGroupSize() == null ? 0 : filter.getMinimumGroupSize())
                .setParameter("minTasks", filter.getMinimumGroupTasks() == null ? 0 : filter.getMinimumGroupTasks())
                .getResultList();

        locations.addAll(groupLocationRepository.findAllLocationsWithDateAfterAndGroupIn(groupsToInclude));
        locations.addAll(meetingLocationRepository.findAllLocationsWithDateAfterAndGroupIn(groupsToInclude));

        return locations;
    }

    public List<ObjectLocation> fetchMeetingLocationsNearUser(User user, GeoLocation geoLocation, Integer radiusInMetres, GeographicSearchType searchType, String searchTerm) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(searchType);
        assertRadius(radiusInMetres);

        GeoLocation searchCentre = geoLocation == null ? fetchBestGuessUserLocation(user.getUid()) : geoLocation;
        if (searchCentre == null) {
            // we can't find anything, so just return blank (leave to client to work out best way to get location)
            // should probably throw an exception here in time, but for the moment (mid clean up) it's somewhat dangerous
            return new ArrayList<>();
        }

        final String usersOwnGroups = "m.ancestorGroup IN (SELECT mm.group FROM Membership mm WHERE mm.user = :user)";
        final String publicGroupsNotUser = "m.isPublic = true AND m.ancestorGroup NOT IN (SELECT mm.group FROM Membership mm WHERE mm.user = :user)";

        final String groupRestriction = GeographicSearchType.PUBLIC.equals(searchType) ? publicGroupsNotUser :
                GeographicSearchType.PRIVATE.equals(searchType) ? usersOwnGroups :
                        "((" + usersOwnGroups +") OR (" + publicGroupsNotUser + "))";

        String strQuery =
                "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(m, l) " +
                        "FROM MeetingLocation l INNER JOIN l.meeting m " +
                        "WHERE " + groupRestriction + " AND m.eventStartDateTime >= :present AND "
                        + GeoLocationUtils.locationFilterSuffix("l.location");

        log.debug("query = {}", strQuery);
        log.info("we have a search location, it looks like: {}", searchCentre);

        TypedQuery<ObjectLocation> query = entityManager.createQuery(strQuery,ObjectLocation.class)
                .setParameter("user", user)
                .setParameter("present", Instant.now());
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

        log.info("Fetching groups, after public fetch, set size = {}", objectLocationSet.size());

        if (searchType.toInt() < PUBLIC_LEVEL) {
            objectLocationSet.addAll(fetchGroupsNearbyWithLocation(location, radiusInMetres,PRIVATE_LEVEL));
        }

        log.info("After private fetch, set size = {}", objectLocationSet.size());


        return new ArrayList<>(objectLocationSet);
    }

    @Override
    @Transactional(readOnly = true)
    public GeoLocation fetchBestGuessUserLocation(String userUid) {
        GeoLocation bestGuess = null;

        Instant cutOffAge = Instant.now().minus(30, ChronoUnit.DAYS);
        List<UserLocationLog> locationLogs = userLocationLogRepository.findByUserUidOrderByTimestampDesc(userUid);

        if (!locationLogs.isEmpty()) {
            log.info("we have {} recent location logs", locationLogs.size());

            Optional<UserLocationLog> bestMatch = locationLogs.stream()
                    .filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_PRECISE))
                    .findFirst();

            log.info("found a new precise log?: {}", bestMatch);

            bestGuess = bestMatch.isPresent() ? bestMatch.get().getLocation() : null;

            if (bestGuess == null) {
                Optional<UserLocationLog> nextTry = locationLogs.stream()
                        .filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_APPROX)).findFirst();
                bestGuess = nextTry.isPresent() ? nextTry.get().getLocation() : null;
                log.info("found an approx location? {}", nextTry.isPresent());
            }

            if (bestGuess == null) {
                log.info("found nothing, using this: {}", locationLogs.get(0));
                bestGuess = locationLogs.get(0).getLocation();
            }

        } else {
            log.info("no recent location logs, going for previously calculated");
            PreviousPeriodUserLocation avgUserLocation = avgLocationRepository
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
}
