package za.org.grassroot.core.domain.geo;

import javax.persistence.*;

@Embeddable
public class ObjectLocation {
	@Column(name = "uid", nullable = false)
	private String uid;
	@Column(name = "name", nullable = false)
	private String name;
	@Column(name = "latitude", nullable = false)
	private double latitude;
	@Column(name = "longitude", nullable = false)
	private double longitude;
	@Column(name = "score", nullable = false)
	private float score;
	@Column(name = "type")
	private String type;

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

	public double getLatitude() {
		return latitude;
	}

	public double getLongitude() {
		return longitude;
	}

	public float getScore() {
		return score;
	}

	public void setScore(float score) {
		this.score = score;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}

	private ObjectLocation() {
		// for JPA
	}

	public ObjectLocation(String uid, String name, double latitude, double longitude, float score, String type) {
		this.uid = uid;
		this.name = name;
		this.latitude = latitude;
		this.longitude = longitude;
		this.score = score;
		this.type = type;
	}

	public ObjectLocation(String uid, String name, double latitude, double longitude) {
		this.uid = uid;
		this.name = name;
		this.latitude = latitude;
		this.longitude = longitude;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		sb.append("latitude=").append(latitude);
		sb.append(", longitude=").append(longitude);
		sb.append(", uid=").append(uid);
		sb.append(", name=").append(name);
		sb.append(", score=").append(score);
		sb.append(", type=").append(type);
		sb.append('}');
		return sb.toString();
	}
}
