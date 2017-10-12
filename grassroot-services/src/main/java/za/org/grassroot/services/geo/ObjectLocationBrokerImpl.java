package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.*;
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

import static za.org.grassroot.services.geo.GeoLocationUtils.KM_PER_DEGREE;

@Service
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {
    private final static Logger logger = LoggerFactory.getLogger(ObjectLocationBroker.class);

    private final static int PRIVATE_LEVEL = -1;
    private final static int PUBLIC_LEVEL = 1;
    private final static int ALL_LEVEL = 0;

    private final EntityManager entityManager;
    private final GroupLocationRepository groupLocationRepository;
    private final MeetingLocationRepository meetingLocationRepository;
    private final UserLocationLogRepository userLocationLogRepository;
    private final PreviousPeriodUserLocationRepository avgLocationRepository;
    private final UserRepository userRepository;


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
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation location, Integer radius) throws InvalidParameterException {
        return fetchGroupsNearbyWithLocation(location, radius, PUBLIC_LEVEL);
    }

    /**
     * TODO: 1) Use the user restrictions and search for public groups
     *
     * Fast nearest-location finder for SQL (MySQL, PostgreSQL, SQL Server)
     * Source: http://www.plumislandmedia.net/mysql/haversine-mysql-nearest-loc/
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupsNearbyWithLocation(GeoLocation location, Integer radius, Integer publicOrPrivate) throws InvalidParameterException {
        logger.info("Fetching group locations ...");

        assertRadius(radius);
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
            " AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)" +
            " AND l.location.latitude " +
            "    BETWEEN :latpoint  - (:radius / :distance_unit) " +
            "        AND :latpoint  + (:radius / :distance_unit) " +
            " AND l.location.longitude " +
            "    BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
            "        AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
            " AND :radius >= (:distance_unit " +
            "         * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
            "         * COS(RADIANS(l.location.latitude)) " +
            "         * COS(RADIANS(:longpoint - l.location.longitude)) " +
            "         + SIN(RADIANS(:latpoint)) " +
            "         * SIN(RADIANS(l.location.latitude))))) ";
        logger.info(query);

        List<ObjectLocation> list = entityManager.createQuery(query,
                ObjectLocation.class)
                .setParameter("date", LocalDate.now())
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("latpoint", location.getLatitude())
                .setParameter("longpoint", location.getLongitude())
                .getResultList();

        logger.info("Now: " + LocalDate.now());
        logger.info("Radius: " + radius);
        logger.info("Location: " + location);

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    /**
     *
     * Fast nearest-location finder for SQL (MySQL, PostgreSQL, SQL Server)
     * Source: http://www.plumislandmedia.net/mysql/haversine-mysql-nearest-loc/
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupsWithinAreaWithLocation(GeoLocation min, GeoLocation max, Integer restriction) {
        logger.info("Fetching group locations ...");

        assertRestriction(restriction);
        assertGeolocation(min);
        assertGeolocation(max);

        // Mount restriction
        String restrictionClause = "";
        if (restriction == PRIVATE_LEVEL) {
            restrictionClause = "g.discoverable = false AND ";
        }
        else if (restriction == PUBLIC_LEVEL) {
            restrictionClause = "g.discoverable = true AND ";
        }

        // Mount query
        String query =
            "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation( " +
            "g.uid, g.groupName, l.location.latitude, l.location.longitude, l.score, 'GROUP', g.description, g.discoverable) " +
            "FROM GroupLocation l " +
            "INNER JOIN l.group g " +
            "WHERE " + restrictionClause +
            "  l.localDate <= :date " +
            "  AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group) " +
            "  AND l.location.latitude " +
            "      BETWEEN :latMin AND :latMax " +
            "  AND l.location.longitude " +
            "      BETWEEN :longMin AND :longMax ";
        logger.info(query);

        List<ObjectLocation> list = entityManager.createQuery(query,
                ObjectLocation.class)
                .setParameter("date", LocalDate.now())
                .setParameter("latMin", min.getLatitude())
                .setParameter("longMin", min.getLongitude())
                .setParameter("latMax", max.getLatitude())
                .setParameter("longMax", max.getLongitude())
                .getResultList();

        logger.info("Now: " + LocalDate.now());
        logger.info("Min: " + min);
        logger.info("Max: " + max);

        return (list.isEmpty() ? new ArrayList<>() : list);
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

    /**
     * TODO: 1) Use the user restrictions
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations (GeoLocation min, GeoLocation max, Integer restriction) {
        logger.info("Fetching meeting locations ...");

        assertRestriction(restriction);
        assertGeolocation(min);
        assertGeolocation(max);

        // Mount restriction
        String restrictionClause = "";
        if (restriction == PRIVATE_LEVEL) {
            restrictionClause = "m.isPublic = false AND ";
        }
        else if (restriction == PUBLIC_LEVEL) {
            restrictionClause = "m.isPublic = true AND ";
        }

        // Mount query
        String query =
            "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(m, l)" +
            "FROM MeetingLocation l " +
            "INNER JOIN l.meeting m " +
            "WHERE " + restrictionClause +
            "  l.calculatedDateTime <= :date " +
            "  AND l.calculatedDateTime = (SELECT MAX(ll.calculatedDateTime) FROM MeetingLocation ll WHERE ll.meeting = l.meeting) " +
            "  AND l.location.latitude " +
            "      BETWEEN :latMin AND :latMax " +
            "  AND l.location.longitude " +
            "      BETWEEN :longMin AND :longMax "
            ;

        logger.info(query);

        List<ObjectLocation> list = entityManager.createQuery(query, ObjectLocation.class)
                .setParameter("date", Instant.now())
                .setParameter("latMin", min.getLatitude())
                .setParameter("longMin", min.getLongitude())
                .setParameter("latMax", max.getLatitude())
                .setParameter("longMax", max.getLongitude())
                .getResultList();

        logger.info("Now: " + Instant.now());
        logger.info("Min: " + min);
        logger.info("Max: " + max);

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    /**
     * TODO: 1) Use the user restrictions
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations (GeoLocation location, Integer radius, Integer restriction) {
        logger.info("Fetching meeting locations ...");

        assertRestriction(restriction);
        assertRadius(radius);
        assertGeolocation(location);

        // Mount restriction
        String restrictionClause = "";
        if (restriction == PRIVATE_LEVEL) {
            restrictionClause = "m.isPublic = false AND ";
        }
        else if (restriction == PUBLIC_LEVEL) {
            restrictionClause = "m.isPublic = true AND ";
        }

        // Mount query
        String query =
            "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
            "  m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
            "  CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime), m.isPublic) " +
            "FROM MeetingLocation l " +
            "INNER JOIN l.meeting m " +
            "WHERE " + restrictionClause +
            "  l.calculatedDateTime <= :date " +
            "  AND l.calculatedDateTime = (SELECT MAX(ll.calculatedDateTime) FROM MeetingLocation ll WHERE ll.meeting = l.meeting) " +
            "  AND l.location.latitude " +
            "      BETWEEN :latpoint  - (:radius / :distance_unit) " +
            "          AND :latpoint  + (:radius / :distance_unit) " +
            "  AND l.location.longitude " +
            "      BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
            "          AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
            "  AND :radius >= (:distance_unit " +
            "           * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
            "           * COS(RADIANS(l.location.latitude)) " +
            "           * COS(RADIANS(:longpoint - l.location.longitude)) " +
            "           + SIN(RADIANS(:latpoint)) " +
            "           * SIN(RADIANS(l.location.latitude))))) ";

        logger.info(query);

        List<ObjectLocation> list = entityManager.createQuery(query, ObjectLocation.class)
                .setParameter("date", Instant.now())
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("latpoint", location.getLatitude())
                .setParameter("longpoint", location.getLongitude())
                .getResultList();

        logger.info("Now: " + Instant.now());
        logger.info("Radius: " + (double)radius);
        logger.info("" + KM_PER_DEGREE);
        logger.info("Location: " + location);

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    /**
     * TODO: IS IT NECESSARY?
     * TODO: 1) Use the user restrictions and search for public groups/meetings
     * TODO: 3) Validate ObjectLocation/group
     */
    @Override
    public List<ObjectLocation> fetchMeetingLocationsByGroup (ObjectLocation group, GeoLocation location, Integer radius) {
        logger.info("Fetching meeting locations by group ...");

        assertRadius(radius);
        assertGeolocation(location);

        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime), m.isPublic) " +
                        "FROM Meeting m " +
                        "INNER JOIN m.parentGroup g, GroupLocation l " +
                        "WHERE l.localDate <= :date " +
                        "AND l.group = g " +
                        "AND g.uid = :guid " +
                        "AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)" +
                        "AND l.location.latitude " +
                        "    BETWEEN :latpoint  - (:radius / :distance_unit) " +
                        "        AND :latpoint  + (:radius / :distance_unit) " +
                        "AND l.location.longitude " +
                        "    BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        "        AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        "AND :radius >= (:distance_unit " +
                        "         * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
                        "         * COS(RADIANS(l.location.latitude)) " +
                        "         * COS(RADIANS(:longpoint - l.location.longitude)) " +
                        "         + SIN(RADIANS(:latpoint)) " +
                        "         * SIN(RADIANS(l.location.latitude))))) ",
                ObjectLocation.class)
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("latpoint", location.getLatitude())
                .setParameter("longpoint", location.getLongitude())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }


    public List<ObjectLocation> fetchMeetingsNearUser(Integer radius, User user, GeoLocation geoLocation){

        assertRadius(radius);
        List<ObjectLocation> list = new ArrayList<>();

        String strQuery =
                "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "  m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "  CONCAT('Where: ', m.eventLocation, 'Date and Time: ', m.eventStartDateTime), m.isPublic) " +
                        "FROM MeetingLocation l " +
                        "INNER JOIN l.meeting m " +
                        "WHERE m.isPublic = true " +
                        "  AND l.location.latitude " +
                        "      BETWEEN :latpoint  - (:radius / :distance_unit) " +
                        "          AND :latpoint  + (:radius / :distance_unit) " +
                        "  AND l.location.longitude " +
                        "      BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        "          AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        "  AND :radius >= (:distance_unit " +
                        "           * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
                        "           * COS(RADIANS(l.location.latitude)) " +
                        "           * COS(RADIANS(:longpoint - l.location.longitude)) " +
                        "           + SIN(RADIANS(:latpoint)) " +
                        "           * SIN(RADIANS(l.location.latitude))))) " +
                        " AND m.ancestorGroup NOT IN(SELECT mm.group FROM Membership mm WHERE mm.user = :user)";

        logger.info("query = {}",strQuery);

        TypedQuery<ObjectLocation> query = entityManager.createQuery(strQuery,ObjectLocation.class)
                .setParameter("user",user)
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE);


        if(geoLocation != null){
            query.setParameter("latpoint", geoLocation.getLatitude())
                    .setParameter("longpoint", geoLocation.getLongitude());
            list = query.getResultList();
        }else{
            Instant fiveDayAgo = Instant.now().minus(5, ChronoUnit.DAYS);
            UserLocationLog userLocationLog = userLocationLogRepository.findByUserUidAndTimestampBetweenNowAndIntervalGiven(user.getUid(),fiveDayAgo);
            if(userLocationLog != null){

                geoLocation = new GeoLocation(userLocationLog.getLocation().getLatitude(),userLocationLog.getLocation().getLongitude());
                query.setParameter("latpoint",geoLocation.getLatitude())
                        .setParameter("longpoint",geoLocation.getLongitude());

                list = query.getResultList();
            }else{

                GroupLocation groupLocation = groupLocationRepository.findByUserUid(user);

                if(groupLocation != null){
                    geoLocation = new GeoLocation(groupLocation.getLocation().getLatitude(),groupLocation.getLocation().getLongitude());
                    if(geoLocation != null){
                        query.setParameter("latpoint",geoLocation.getLatitude())
                                .setParameter("longpoint",geoLocation.getLongitude());

                        list = query.getResultList();
                    }

                } else {
                    list = new ArrayList<>();
                }
            }
        }
        return (list.isEmpty() ? new ArrayList<>() : list);
    }


    @Override
    public List<ObjectLocation> fetchUserGroupsNearThem(String userUid, GeoLocation location, Integer radiusMetres, String filterTerm) throws InvalidParameterException {
        User user = userRepository.findOneByUid(userUid);

        assertGeolocation(location);

        List<ObjectLocation> objectLocations;
        String query =
                "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation( " +
                        "g.uid, g.groupName, l.location.latitude, l.location.longitude, l.score, 'GROUP', g.description, g.discoverable) " +
                        "FROM GroupLocation l " +
                        "INNER JOIN l.group g " +
                        "INNER JOIN g.parent m, Membership mm " +
                        "WHERE l.location.latitude " +
                        "    BETWEEN :latpoint  - (:radiusMetres / :distance_unit) " +
                        "        AND :latpoint  + (:radiusMetres / :distance_unit) " +
                        " AND l.location.longitude " +
                        "    BETWEEN :longpoint - (:radiusMetres / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        "        AND :longpoint + (:radiusMetres / (:distance_unit * COS(RADIANS(:latpoint)))) " +
                        " AND :radiusMetres >= (:distance_unit " +
                        "         * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
                        "         * COS(RADIANS(l.location.latitude)) " +
                        "         * COS(RADIANS(:longpoint - l.location.longitude)) " +
                        "         + SIN(RADIANS(:latpoint)) " +
                        "         * SIN(RADIANS(l.location.latitude))))) " +
                        " AND mm.user = :user " +
                        " AND g.description LIKE LOWER (CONCAT('%',:term , '%'))";

        TypedQuery<ObjectLocation> objectLocationTypedQuery = entityManager.createQuery(query,ObjectLocation.class)
                .setParameter("user",user)
                .setParameter("radiusMetres", (double)radiusMetres)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("term",filterTerm)
                .setParameter("latpoint",location.getLatitude())
                .setParameter("longpoint",location.getLongitude());

        objectLocations = objectLocationTypedQuery.getResultList();
        return objectLocations;
    }

    @Override
    public List<ObjectLocation> fetchGroupsNearby(GeoLocation location, Integer radius, String searchTerm, String filterTerm, String userUid) throws InvalidParameterException {

        assertGeolocation(location);

        Integer publicPrivateOrBoth = searchTerm.toLowerCase().equals("all") ? 0 : (searchTerm.toLowerCase().equals("public") ? 1 : -1);

        Set<ObjectLocation> objectLocationSet = new HashSet<>();

        if (publicPrivateOrBoth > PRIVATE_LEVEL) {
            objectLocationSet.addAll(fetchPublicGroupsNearbyWithLocation(location,radius));
        }

        if (publicPrivateOrBoth < PUBLIC_LEVEL) {
            objectLocationSet.addAll(fetchGroupsNearbyWithLocation(location,radius,PRIVATE_LEVEL));
        }

        logger.info("Set Size = {}",objectLocationSet.size());

        return new ArrayList<>(objectLocationSet);
    }

    @Override
    @Transactional(readOnly = true)
    public GeoLocation fetchBestGuessUserLocation(String userUid) {
        GeoLocation bestGuess = null;

        Instant cutOffAge = Instant.now().minus(30, ChronoUnit.DAYS);
        List<UserLocationLog> locationLogs = userLocationLogRepository.findByUserUidOrderByTimestampDesc(userUid);

        if (!locationLogs.isEmpty()) {

            Optional<UserLocationLog> bestMatch = locationLogs.stream()
                    .filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_PRECISE))
                    .findFirst();

            bestGuess = bestMatch.isPresent() ? bestMatch.get().getLocation() : null;

            if (bestGuess == null) {
                Optional<UserLocationLog> nextTry = locationLogs.stream()
                        .filter(ofSourceNewerThan(cutOffAge, LocationSource.LOGGED_APPROX)).findFirst();
                bestGuess = nextTry.isPresent() ? nextTry.get().getLocation() : null;
            }

            if (bestGuess == null) {
                bestGuess = locationLogs.get(0).getLocation();
            }

        } else {
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


    private void assertRestriction (Integer restriction) throws InvalidParameterException {
        if (restriction == null) {
            throw new InvalidParameterException("Invalid restriction object.");
        } else if (restriction < PRIVATE_LEVEL || restriction > ALL_LEVEL) {
            throw new InvalidParameterException("Invalid restriction object.");
        }
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
