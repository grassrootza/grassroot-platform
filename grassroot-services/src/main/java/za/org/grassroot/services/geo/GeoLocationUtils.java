package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.geo.GeoLocation;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.UserInterfaceType;

import javax.persistence.Query;
import java.util.List;
import java.util.Objects;

public class GeoLocationUtils {

	protected final static double KM_PER_DEGREE = 111.045;
	public final static int DEFAULT_RADIUS = 5;

	private GeoLocationUtils() {
		// utility
	}

	public static GeoLocation centralLocation(List<GeoLocation> geoLocations) {
		Objects.requireNonNull(geoLocations);
		if (geoLocations.isEmpty()) {
			return null;
		}
		int locationCount = geoLocations.size();

		if (locationCount == 1) {
			return geoLocations.iterator().next();
		}

		double x = 0;
		double y = 0;
		double z = 0;

		for (GeoLocation geoLocation : geoLocations) {
			double latitude = Math.toRadians(geoLocation.getLatitude()) * Math.PI / 180;
			double longitude = Math.toRadians(geoLocation.getLongitude()) * Math.PI / 180;

			x += Math.cos(latitude) * Math.cos(longitude);
			y += Math.cos(latitude) * Math.sin(longitude);
			z += Math.sin(latitude);
		}

		x = x / locationCount;
		y = y / locationCount;
		z = z / locationCount;

		double centralLongitude = Math.atan2(y, x);
		double centralSquareRoot = Math.sqrt(x * x + y * y);
		double centralLatitude = Math.atan2(z, centralSquareRoot);

		double midPointLatitude = Math.toDegrees(centralLatitude * 180 / Math.PI);
		double midPointLongitude = Math.toDegrees(centralLongitude * 180 / Math.PI);
		return new GeoLocation(midPointLatitude, midPointLongitude);
	}

	public static Address convertGeoCodeToAddress(InvertGeoCodeAddress resultAddress, User user,
												  GeoLocation location, UserInterfaceType interfaceType, boolean makePrimary) {
		Objects.requireNonNull(resultAddress);
		Address address = new Address(user,
				resultAddress.getHouseNumber() == null ? "" : resultAddress.getHouseNumber(), // important else messes w/duplicate finding
				resultAddress.getRoad() == null ? "" : resultAddress.getRoad(),
				resultAddress.getSuburb() == null ? "" : resultAddress.getSuburb(),
				makePrimary);
		if (location != null) {
			address.setLocation(location);
			address.setLocationSource(LocationSource.convertFromInterface(interfaceType));
		}
		return address;
	}

	public static String locationFilterSuffix(String locationEntityLabel) {
		return String.format("%1$s.latitude " +
				"    BETWEEN :latpoint  - (:radius / :distance_unit) " +
				"        AND :latpoint  + (:radius / :distance_unit) " +
				"AND %1$s.longitude " +
				"    BETWEEN :longpoint - (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
				"        AND :longpoint + (:radius / (:distance_unit * COS(RADIANS(:latpoint)))) " +
				"AND :radius >= (:distance_unit " +
				"         * DEGREES(ACOS(COS(RADIANS(:latpoint)) " +
				"         * COS(RADIANS(%1$s.latitude)) " +
				"         * COS(RADIANS(:longpoint - %1$s.longitude)) " +
				"         + SIN(RADIANS(:latpoint)) " +
				"         * SIN(RADIANS(%1$s.latitude)))))", locationEntityLabel);
	}

	public static void addLocationParamsToQuery(Query query, GeoLocation location, Integer radius) {
		query.setParameter("radius", radius != null ? (double) radius : (double) DEFAULT_RADIUS);
		query.setParameter("distance_unit", KM_PER_DEGREE);
		query.setParameter("latpoint", location.getLatitude());
		query.setParameter("longpoint", location.getLongitude());
	}

}
