package za.org.grassroot.services.geo;

import org.apache.http.client.utils.URIBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.repository.GroupLocationRepository;
import za.org.grassroot.core.repository.MeetingLocationRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.group.GroupLocationFilter;

import javax.persistence.EntityManager;
import java.net.URISyntaxException;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {
    private final static double MIN_LATITUDE = -90.00;
    private final static double MAX_LATITUDE = 90.00;
    private final static double MIN_LONGITUDE = -180.00;
    private final static double MAX_LONGITUDE = 180.00;

    private static final Logger logger = LoggerFactory.getLogger(ObjectLocationBroker.class);
    private final EntityManager entityManager;
    private final GroupLocationRepository groupLocationRepository;
    private final MeetingLocationRepository meetingLocationRepository;
    private final RestTemplate restTemplate;

    @Value("${grassroot.geocoding.api.url:http://nominatim.openstreetmap.org/reverse}")
    private String geocodingApiUrl;

    @Autowired
    public ObjectLocationBrokerImpl(EntityManager entityManager, GroupLocationRepository groupLocationRepository, MeetingLocationRepository meetingLocationRepository, RestTemplate restTemplate) {
        this.entityManager = entityManager;
        this.groupLocationRepository = groupLocationRepository;
        this.meetingLocationRepository = meetingLocationRepository;
        this.restTemplate = restTemplate;
    }

    /**
     * TODO: 1) Use the user restrictions and search for public groups
     * TODO: 2) Use the radius to search
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupLocations (GeoLocation location, Integer radius) throws InvalidParameterException {
        logger.info("Fetching group locations ...");

        assertRadius(radius);
        assertGeolocation(location);

        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation( " +
                        "g.uid, g.groupName, l.location.latitude, " + "l.location.longitude, l.score, 'GROUP', g.description) " +
                        "FROM GroupLocation l " +
                        "INNER JOIN l.group g " +
                        "WHERE g.discoverable = true " +
                        "AND l.localDate <= :date " +
                        "AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)",
                ObjectLocation.class).setParameter("date", LocalDate.now()).getResultList();

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

    @Override
    public String getReverseGeoCodedAddress(GeoLocation location) {
        Objects.requireNonNull(location);
        try {
            InvertGeoCodeResult result = restTemplate.getForObject(
                    invertGeoCodeRequestURI(location).build(), InvertGeoCodeResult.class);
            return result.getDisplayName();
        } catch (URISyntaxException|NullPointerException|HttpClientErrorException e) {
            e.printStackTrace();
            return "Undetected";
        }
    }

    @Override
    public InvertGeoCodeResult getReviseGeoCodeAddressFullGeoLocation(GeoLocation location) {
        try {
            return restTemplate.getForObject(
                    invertGeoCodeRequestURI(location).build(),
                    InvertGeoCodeResult.class);
        } catch (URISyntaxException|NullPointerException|HttpClientErrorException e) {
            e.printStackTrace();
            return null;
        }
    }

    private URIBuilder invertGeoCodeRequestURI(GeoLocation location) throws URISyntaxException {
        URIBuilder uriBuilder = new URIBuilder(geocodingApiUrl);
        uriBuilder.addParameter("format", "json");
        uriBuilder.addParameter("lat", String.valueOf(location.getLatitude()));
        uriBuilder.addParameter("lon", String.valueOf(location.getLongitude()));
        uriBuilder.addParameter("zoom", "18");
        return uriBuilder;
    }

    /**
     * TODO: 1) Use the user restrictions and search for public groups/meetings
     * TODO: 2) Use the radius to search
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations(GeoLocation location, Integer radius) {
        logger.info("Fetching meeting locations ...");

        assertRadius(radius);
        assertGeolocation(location);

        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime)) " +
                        "FROM MeetingLocation l " +
                        "INNER JOIN l.meeting m " +
                        "WHERE m.isPublic = true " +
                        "AND l.calculatedDateTime <= :date " +
                        "AND l.calculatedDateTime = (SELECT MAX(ll.calculatedDateTime) FROM MeetingLocation ll WHERE ll.meeting = l.meeting) ",
                ObjectLocation.class).setParameter("date", Instant.now()).getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    /**
     * TODO: IS IT NECESSARY?
     * TODO: 1) Use the user restrictions and search for public groups/meetings
     * TODO: 2) Use the radius to search
     * TODO: 3) Validate ObjectLocation/group
     */
    @Override
    public List<ObjectLocation> fetchMeetingLocationsByGroup (ObjectLocation group, GeoLocation location, Integer radius) {
        logger.info("Fetching meeting locations by group ...");

        assertRadius(radius);
        assertGeolocation(location);

        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime)) " +
                        "FROM Meeting m " +
                        "INNER JOIN m.parentGroup g, GroupLocation l " +
                        "WHERE l.localDate <= :date " +
                        "AND l.group = g " +
                        "AND g.uid = :guid " +
                        "AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)",
                ObjectLocation.class).setParameter("date", LocalDate.now()).setParameter("guid", group.getUid()).getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    private void assertRadius (Integer radius) throws InvalidParameterException {
        if (radius == null || radius <= 0) {
            throw new InvalidParameterException("Invalid radius object.");
        }
    }
    private void assertGeolocation (GeoLocation location) throws InvalidParameterException {
        if (location == null || location.getLatitude() < MIN_LATITUDE || location.getLatitude() > MAX_LATITUDE ||
                location.getLongitude() < MIN_LONGITUDE || location.getLongitude() > MAX_LONGITUDE) {
            throw new InvalidParameterException("Invalid GeoLocation object.");
        }
    }
}
