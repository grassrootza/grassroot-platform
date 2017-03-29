package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;

import java.util.List;

public interface ObjectLocationBroker {
	List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius);
}
