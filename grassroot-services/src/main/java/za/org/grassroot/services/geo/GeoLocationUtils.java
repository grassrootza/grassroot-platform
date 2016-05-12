package za.org.grassroot.services.geo;

import za.org.grassroot.core.domain.geo.GeoLocation;

import java.util.List;
import java.util.Objects;

public class GeoLocationUtils {
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
}
