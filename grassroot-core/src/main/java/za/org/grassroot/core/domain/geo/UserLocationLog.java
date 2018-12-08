package za.org.grassroot.core.domain.geo;

import lombok.Getter;
import lombok.Setter;
import za.org.grassroot.core.enums.LocationSource;
import za.org.grassroot.core.enums.Province;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.EnumType;
import javax.persistence.Enumerated;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Getter
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

	@Enumerated(EnumType.STRING)
	@Column(name = "source", length = 50, nullable = false)
	private LocationSource locationSource;

	@Enumerated(EnumType.STRING)
    @Column(name = "province", length = 50)
    @Setter private Province province;

	@Column(name="municipality_id", length = 50)
	@Setter private String municipalityId; // to store more simply

    @Column(name="municipality_name", length = 255)
    @Setter private String municipalityName; // not normalized, but API can't do bulk queries, so alternative is 000s of queries for no reason

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

	public void setLocationData(Province province, String municipalityId, String municipalityName) {
	    if (province != null) {
	        this.province = province;
        }
	    this.municipalityId = municipalityId;
	    this.municipalityName = municipalityName;
    }

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
