package za.org.grassroot.services.geo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import javax.persistence.EntityManager;
import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {
    private final static double MIN_LATITUDE = -90.00;
    private final static double MAX_LATITUDE = 90.00;
    private final static double MIN_LONGITUDE = -180.00;
    private final static double MAX_LONGITUDE = 180.00;

    private final static double KM_PER_DEGREE = 111.045;

    private static final Logger logger = LoggerFactory.getLogger(ObjectLocationBroker.class);
    private final EntityManager entityManager;

    @Autowired
    public ObjectLocationBrokerImpl (EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Fast nearest-location finder for SQL (MySQL, PostgreSQL, SQL Server)
     * Source: http://www.plumislandmedia.net/mysql/haversine-mysql-nearest-loc/
     *
     * SELECT zip, primary_city,
     *    latitude, longitude, distance
     * FROM (
     *    SELECT z.zip,
     *      z.primary_city,
     *      z.latitude, z.longitude,
     *      p.radius,
     *      p.distance_unit
     *        * DEGREES(ACOS(COS(RADIANS(p.latpoint))
     *        * COS(RADIANS(z.latitude))
     *        * COS(RADIANS(p.longpoint - z.longitude))
     *        + SIN(RADIANS(p.latpoint))
     *        * SIN(RADIANS(z.latitude)))) AS distance
     *    FROM zip AS z
     *    JOIN (
     *      SELECT 42.81  AS latpoint,       -- these are the query parameters
     *             -70.81 AS longpoint,
     *             50.0 AS radius,           -- A 50 km of maximum radius
     *             111.045 AS distance_unit  -- The distance is 111.045 km per degree
     *    ) AS p ON 1=1
     *    WHERE z.latitude
     *      BETWEEN p.latpoint  - (p.radius / p.distance_unit)
     *        AND p.latpoint  + (p.radius / p.distance_unit)
     *      AND z.longitude
     *      BETWEEN p.longpoint - (p.radius / (p.distance_unit * COS(RADIANS(p.latpoint))))
     *        AND p.longpoint + (p.radius / (p.distance_unit * COS(RADIANS(p.latpoint))))
     * ) AS d
     * WHERE distance <= radius
     * ORDER BY distance
     * LIMIT 15
     */

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
                        "g.uid, g.groupName, l.location.latitude, l.location.longitude, l.score, 'GROUP', g.description, false) " +
                        "FROM GroupLocation l " +
                        "INNER JOIN l.group g " +
                        "WHERE g.discoverable = true " +
                        "AND l.localDate <= :date " +
                        "AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)",
                ObjectLocation.class).setParameter("date", LocalDate.now()).getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    /**
     * TODO: 1) Use the user restrictions and search for public groups/meetings
     * TODO: 2) Use the radius to search
     */
    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations (GeoLocation location, Integer radius) {
        logger.info("Fetching meeting locations ...");

        assertRadius(radius);
        assertGeolocation(location);

        /**
        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime), m.isPublic) " +
                        "FROM MeetingLocation l " +
                        "INNER JOIN l.meeting m " +
                        "WHERE m.isPublic = true " +
                        "AND l.calculatedDateTime <= :date " +
                        "AND l.calculatedDateTime = (SELECT MAX(ll.calculatedDateTime) FROM MeetingLocation ll WHERE ll.meeting = l.meeting) ",
                ObjectLocation.class).setParameter("date", Instant.now()).getResultList();
        **/
        List<ObjectLocation> list = entityManager.createQuery("SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "  m.uid, m.name, l.location.latitude, l.location.longitude, l.score, 'MEETING', " +
                        "  CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime), m.isPublic) " +
                        "FROM MeetingLocation l " +
                        "INNER JOIN l.meeting m " +
                        "WHERE m.isPublic = true " +
                        "  AND l.calculatedDateTime <= :date " +
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
                        "           * SIN(RADIANS(l.location.latitude))))) ",
                ObjectLocation.class)
                .setParameter("date", Instant.now())
                .setParameter("radius", (double)radius)
                .setParameter("distance_unit", KM_PER_DEGREE)
                .setParameter("latpoint", location.getLatitude())
                .setParameter("longpoint", location.getLongitude())
                .getResultList();


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
                        "CONCAT('<strong>Where: </strong>', m.eventLocation, '<br/><strong>Date and Time: </strong>', m.eventStartDateTime), m.isPublic) " +
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
