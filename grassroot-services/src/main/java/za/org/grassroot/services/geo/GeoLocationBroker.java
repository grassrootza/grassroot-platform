package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.geo.ObjectLocation;
import za.org.grassroot.core.domain.geo.PreviousPeriodUserLocation;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.security.InvalidParameterException;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;

public interface GeoLocationBroker {

	void logUserLocation(String userUid, double latitude, double longitude, Instant time, UserInterfaceType interfaceType);

	// means user has given us permission to log their location, through some action, so record it, and update entity
	void logUserUssdPermission(String userUid, String entityToUpdateUid, JpaEntityType entityType, boolean singleTrackPermission);

	void calculatePreviousPeriodUserLocations(LocalDate localDate);

	void calculateGroupLocation(String groupUid, LocalDate localDate);

	void calculateGroupLocationInstant(String groupUid, GeoLocation location, UserInterfaceType coordSourceInterface);

	// used for recalculating / improving existing meetings
	void calculateMeetingLocationScheduled(String eventUid, LocalDate localDate);

	// used for a meeting that has just been called
	void calculateMeetingLocationInstant(String eventUid, GeoLocation location, UserInterfaceType coordSourceInterface);

	void calculateTodoLocationInstant(String todoUid, GeoLocation location, UserInterfaceType coordSourceInterface);

	CenterCalculationResult calculateCenter(Set<String> userUids, LocalDate date);

	PreviousPeriodUserLocation fetchUserLocation(String userUid, LocalDate localDate);

	PreviousPeriodUserLocation fetchUserLocation(String userUid);

	List<User> fetchUsersWithRecordedAverageLocations(LocalDate localDate);

	GroupLocation fetchGroupLocationWithScoreAbove(String groupUid, LocalDate localDate, float score);

	List<Group> fetchGroupsWithRecordedAverageLocations();

	List<Group> fetchGroupsWithRecordedLocationsFromSet(Set<Group> referenceSet);

	List<double[]> fetchUserLatitudeLongitudeInAvgPeriod(String userUid, LocalDate localDate);

	List<ObjectLocation> fetchGroupsNearby(String userUid, GeoLocation location, Integer radiusInMetres,
										  String filterTerm, GeographicSearchType searchType)
			throws InvalidParameterException;

	List<ObjectLocation> fetchPublicGroupsNearbyWithLocation(GeoLocation geoLocation, Integer radiusInMetres)
			throws InvalidParameterException;

	List<ObjectLocation> fetchMeetingLocationsNearUser(User user, GeoLocation geoLocation, Integer radiusInMetres, GeographicSearchType searchType, String searchTerm)
			throws InvalidParameterException;

	GeoLocation fetchBestGuessUserLocation(String userUid);
}
