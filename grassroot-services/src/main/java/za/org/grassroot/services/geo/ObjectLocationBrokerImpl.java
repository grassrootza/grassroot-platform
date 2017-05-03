package za.org.grassroot.services.geo;

import org.omg.CORBA.DynAnyPackage.Invalid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.exception.InvalidGeoLocationException;

import javax.persistence.EntityManager;
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

    private static final Logger logger = LoggerFactory.getLogger(ObjectLocationBroker.class);
    private final EntityManager entityManager;

    @Autowired
    public ObjectLocationBrokerImpl(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) throws InvalidGeoLocationException {
        // TODO: 1) Use the user restrictions and search for public groups
        // TODO: 2) Use the radius to search
        // TODO: 3) Param check - throw relevant exception
        // TODO: The latitude must be a number between -90 and 90 and the longitude between -180 and 180.

        if(geoLocation == null ||
            geoLocation.getLatitude() < MIN_LATITUDE || geoLocation.getLatitude() > MAX_LATITUDE ||
            geoLocation.getLongitude() < MIN_LONGITUDE || geoLocation.getLongitude() > MAX_LONGITUDE){
            throw new InvalidGeoLocationException("Invalid GeoLocation object.");
        }

        List<ObjectLocation> list = entityManager.createQuery(
                "SELECT NEW za.org.grassroot.core.domain.geo.ObjectLocation(" +
                        "g.uid, g.groupName, l.location.latitude, " + "l.location.longitude, l.score, 'GROUP', g.description) " +
                "FROM GroupLocation l " +
                "INNER JOIN l.group g " +
                "WHERE g.discoverable = true " +
                "AND l.localDate <= :date " +
                "AND l.localDate = (SELECT MAX(ll.localDate) FROM GroupLocation ll WHERE ll.group = l.group)",
                ObjectLocation.class)
            .setParameter("date", LocalDate.now())
            .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius) {
        // TODO: 1) Use the user restrictions and search for public groups/meetings
        // TODO: 2) Use the radius to search
        logger.info("looking for meeting locations ...");
        List<ObjectLocation> list = entityManager.createQuery(
                    "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " m.uid"
                        + ",m.name"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'MEETING'"
                        + ",CONCAT('<strong>Where: </strong>',m.eventLocation,'<br/><strong>Date and Time: </strong>',m.eventStartDateTime)"
                        + ")"
                        + " from MeetingLocation l"
                        + " inner join l.meeting m"
                        + " where m.isPublic = true and l.calculatedDateTime <= :date"
                        + " and l.calculatedDateTime = (select max(ll.calculatedDateTime) from MeetingLocation ll where ll.meeting = l.meeting)",
                   ObjectLocation.class
                )
                .setParameter("date", Instant.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    @Override
    public List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius) {
        // TODO: 1) Use the user restrictions and search for public groups/meetings
        // TODO: 2) Use the radius to search
        List<ObjectLocation> list = entityManager.createQuery(
                "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " m.uid"
                        + ",m.name"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'MEETING'"
                        + ",CONCAT('<strong>Where: </strong>',m.eventLocation,'<br/><strong>Date and Time: </strong>',m.eventStartDateTime)"
                        + ")"
                        + " from Meeting m"
                        + " inner join m.parentGroup g"
                        + ",GroupLocation l"
                        + " where l.localDate <= :date"
                        + " and l.group = g"
                        + " and g.uid = :guid"
                        + " and l.localDate = (select max(ll.localDate) from GroupLocation ll where ll.group = l.group)",
                    ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }
}
