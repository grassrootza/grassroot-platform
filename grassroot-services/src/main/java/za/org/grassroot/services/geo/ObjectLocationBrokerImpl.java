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
        List<ObjectLocation> list = entityManager.createQuery(
                    "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " g.uid as uid"
                        + ",g.groupName as name"
                        + ",l.location.latitude as latitude"
                        + ",l.location.longitude as longitude"
                        + ",l.score as score"
                        + ",'MEETING' as type"
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
    public List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius) {
        List<ObjectLocation> list = entityManager.createQuery(
                "select NEW za.org.grassroot.core.domain.geo.ObjectLocation("
                        + " g.uid as uid"
                        + ",g.groupName as name"
                        + ",l.location.latitude as latitude"
                        + ",l.location.longitude as longitude"
                        + ",l.score as score"
                        + ",'MEETING' as type"
                        + ")"
                        + " from Meeting m"
                        + " inner m.ancestorGroup l"
                        + " inner join l.group g"
                        + " where l.localDate <= :date"
                        + " and g.uid = :guid",
                    ObjectLocation.class
                )
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<>() : list);
    }
}
