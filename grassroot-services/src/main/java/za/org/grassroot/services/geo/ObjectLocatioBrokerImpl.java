package za.org.grassroot.services.geo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import javax.persistence.EntityManager;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
public class ObjectLocatioBrokerImpl implements ObjectLocationBroker {
    @Autowired
    private EntityManager entityManager;

    @Override
    public List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) {

        List list = entityManager.createQuery(
                    "select distinct"
                    + " l.location.latitude as latitude"
                    + ",l.location.longitude as longitude"
                    + ",l.score as score"
                    + ",'GROUP' as type"
                    + ",g.groupName as name"
                    + ",g.uid as uid"
                    + " from GroupLocation l"
                    + " inner join l.group g"
                    + " where l.localDate <= :date"
                )
                .setParameter("date", LocalDate.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<ObjectLocation>() : list);
    }

    @Override
    public List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius) {
        List list = entityManager.createQuery(
                "select distinct"
                        + " l.location.latitude as latitude"
                        + ",l.location.longitude as longitude"
                        + ",l.score as score"
                        + ",'MEETING' as type"
                        + ",g.groupName as name"
                        + ",g.uid as uid"
                        + " from GroupLocation l"
                        + " inner join l.group g"
                        + " where l.localDate <= :date"
                )
                .setParameter("date", LocalDate.now())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<ObjectLocation>() : list);
    }

    @Override
    public List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius) {
        List list = entityManager.createQuery(
                "select distinct"
                        + " l.location.latitude as latitude"
                        + ",l.location.longitude as longitude"
                        + ",l.score as score"
                        + ",'MEETING' as type"
                        + ",g.groupName as name"
                        + ",g.uid as uid"
                        + " from Meeting m"
                        + " inner m.ancestorGroup l"
                        + " inner join l.group g"
                        + " where l.localDate <= :date"
                        + " and g.uid = :guid"
                )
                .setParameter("date", LocalDate.now())
                .setParameter("guid", group.getUid())
                .getResultList();

        return (list.isEmpty() ? new ArrayList<ObjectLocation>() : list);
    }
}
