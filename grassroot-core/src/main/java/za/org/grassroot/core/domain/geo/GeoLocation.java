package za.org.grassroot.core.domain.geo;

import javax.persistence.Column;
import javax.persistence.Embeddable;

@Embeddable
public class GeoLocation {
	@Column(name = "latitude", nullable = false)
	private double latitude;

	@Column(name = "longitude", nullable = false)
	private double longitude;

	private GeoLocation() {
		// for JPA
	}

	public GeoLocation(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		GeoLocation that = (GeoLocation) o;

		if (Double.compare(that.latitude, latitude) != 0) {
			return false;
		}
		if (Double.compare(that.longitude, longitude) != 0) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result;
		long temp;
		temp = Double.doubleToLongBits(latitude);
		result = (int) (temp ^ (temp >>> 32));
		temp = Double.doubleToLongBits(longitude);
		result = 31 * result + (int) (temp ^ (temp >>> 32));
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		sb.append("lat=").append(latitude);
		sb.append(", long=").append(longitude);
		sb.append('}');
		return sb.toString();
	}
}
