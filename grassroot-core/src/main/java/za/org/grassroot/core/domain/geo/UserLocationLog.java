package za.org.grassroot.core.domain.geo;

import za.org.grassroot.core.enums.LocationSource;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "user_location_log",
		uniqueConstraints = @UniqueConstraint(name = "uk_user_location_log_uid", columnNames = "uid"))
public class UserLocationLog {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Column(name = "uid", length = 50, nullable = false)
	private String uid;

	@Column(name="timestamp", nullable = false)
	private Instant timestamp;

	@Column(name="user_uid", length = 50, nullable = false)
	private String userUid;

	private GeoLocation location;

	@Column(name = "source", nullable = false)
	@Enumerated(EnumType.STRING)
	private LocationSource locationSource;

	private UserLocationLog() {
		// for JPA
	}

	public UserLocationLog(Instant timestamp, String userUid, GeoLocation location, LocationSource locationSource) {
		this.uid = UUID.randomUUID().toString();
		this.timestamp = Objects.requireNonNull(timestamp);
		this.userUid = Objects.requireNonNull(userUid);
		this.location = Objects.requireNonNull(location);
		this.locationSource = Objects.requireNonNull(locationSource);
	}

	public Long getId() {
		return id;
	}

	public String getUid() {
		return uid;
	}

	public Instant getTimestamp() {
		return timestamp;
	}

	public String getUserUid() {
		return userUid;
	}

	public GeoLocation getLocation() {
		return location;
	}

	public LocationSource getLocationSource() { return locationSource; }

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		UserLocationLog userLocationLog = (UserLocationLog) o;

		return uid != null ? uid.equals(userLocationLog.uid) : userLocationLog.uid == null;

	}

	@Override
	public int hashCode() {
		return uid != null ? uid.hashCode() : 0;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("GeoLocationLog{");
		sb.append("timestamp=").append(timestamp);
		sb.append(", userUid='").append(userUid).append('\'');
		sb.append(", location=").append(location);
		sb.append(", source=").append(locationSource);
		sb.append(", id=").append(id);
		sb.append('}');
		return sb.toString();
	}
}
