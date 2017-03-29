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
}
