package za.org.grassroot.services.geo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectLocationBrokerImpl implements ObjectLocationBroker {
    @Autowired
    private EntityManager entityManager;

    @Override
    public List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) {

        // TODO: 1) Use the user restrictions and search for public groups
        // TODO: 2) Use the radius to search
        List<ObjectLocation> list = entityManager.createQuery(
                "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " g.uid"
                        + ",g.groupName"
                        + ",l.location.latitude"
                        + ",l.location.longitude"
                        + ",l.score"
                        + ",'GROUP'"
                        + ")"
                        + " from GroupLocation l"
                        + " inner join l.group g"
                        + " where l.localDate <= :date",
                ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }

    @Override
    public List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius) {
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
                        + ")"
                        + " from MeetingLocation l"
                        + " inner join l.meeting m"
                        + " where l.localDate <= :date",
                   ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
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
                        + ")"
                        + " from Meeting m"
                        + " inner join m.parentGroup g"
                        + ",GroupLocation l"
                        + " where l.localDate <= :date"
                        + " and l.group = g"
                        + " and g.uid = :guid",
                    ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }
}
