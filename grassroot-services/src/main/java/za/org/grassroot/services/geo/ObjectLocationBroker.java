package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.dto.group.GroupMinimalDTO;
import za.org.grassroot.services.group.GroupLocationFilter;
import za.org.grassroot.services.group.GroupSearchResultDTO;

import java.security.InvalidParameterException;
import java.util.List;

public interface ObjectLocationBroker {

    List<ObjectLocation> fetchGroupsNearby(String userUid, GeoLocation location, Integer radiusInMetres,
                                           String filterTerm, GeographicSearchType searchType)
        throws InvalidParameterException;

    List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation geoLocation, Integer radiusInMetres)
            throws InvalidParameterException;

    List<ObjectLocation> fetchLocationsWithFilter(GroupLocationFilter filter);

    List<ObjectLocation> fetchMeetingLocationsNearUser(User user, GeoLocation geoLocation, Integer radiusInMetres, GeographicSearchType searchType, String searchTerm)
            throws InvalidParameterException;

    GeoLocation fetchBestGuessUserLocation(String userUid);

}
