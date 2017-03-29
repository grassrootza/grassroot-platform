package za.org.grassroot.core.domain.geo;

import javax.persistence.Embeddable;

@Embeddable
public class ObjectLocation {
	private String uid;
	private String name;
	private double latitude;
	private double longitude;

	public String getUid() {
		return uid;
	}

	public void setUid(String uid) {
		this.uid = uid;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public void setLatitude(double latitude) {
		this.latitude = latitude;
	}

	public void setLongitude(double longitude) {
		this.longitude = longitude;
	}

	private ObjectLocation() {
		// for JPA
	}

	public ObjectLocation(String uid, String name, double latitude, double longitude) {
		this.uid = uid;
		this.name = name;
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
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		sb.append("latitude=").append(latitude);
		sb.append(", longitude=").append(longitude);
		sb.append(", uid=").append(uid);
		sb.append(", name=").append(name);
		sb.append('}');
		return sb.toString();
	}
}
