package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.services.group.GroupLocationFilter;

import java.security.InvalidParameterException;
import java.util.List;

public interface ObjectLocationBroker {

    List<ObjectLocation> fetchGroupLocations(GeoLocation geoLocation, Integer radius) throws InvalidParameterException;
    List<ObjectLocation> fetchMeetingLocations(GeoLocation geoLocation, Integer radius) throws InvalidParameterException;
    List<ObjectLocation> fetchMeetingLocationsByGroup(ObjectLocation group, GeoLocation geoLocation, Integer radius)
            throws InvalidParameterException;
    List<ObjectLocation> fetchLocationsWithFilter(GroupLocationFilter filter);

}
