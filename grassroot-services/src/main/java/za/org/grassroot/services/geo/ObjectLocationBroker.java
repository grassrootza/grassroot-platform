package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.exception.InvalidGeoLocationException;

import java.util.List;

public interface ObjectLocationBroker {
    List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) throws InvalidGeoLocationException;
    List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius);
    List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius);
}
