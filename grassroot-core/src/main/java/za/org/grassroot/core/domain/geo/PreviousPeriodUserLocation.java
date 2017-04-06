package za.org.grassroot.core.domain.geo;

import javax.persistence.Column;
import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "prev_period_user_location")
public class PreviousPeriodUserLocation implements LocationHolder {
	@EmbeddedId
	private UserAndLocalDateKey key;

	private GeoLocation location;

	@Column(name = "log_count", nullable = false)
	private int logCount;

	private PreviousPeriodUserLocation() {
		// for JPA
	}

	public PreviousPeriodUserLocation(UserAndLocalDateKey key, GeoLocation location, int logCount) {
		this.key = Objects.requireNonNull(key);
		this.location = Objects.requireNonNull(location);
		if (logCount < 1) {
			throw new IllegalArgumentException("Log count has to be positive number, but is: " + logCount);
		}
		this.logCount = logCount;
	}

	public UserAndLocalDateKey getKey() {
		return key;
	}

	public GeoLocation getLocation() {
		return location;
	}

	public boolean hasLocation() { return true; }

	public int getLogCount() {
		return logCount;
	}

	public String toString() {
		final StringBuilder sb = new StringBuilder("PreviousPeriodUserLocation{");
		sb.append("key=").append(key);
		sb.append(", location=").append(location);
		sb.append(", logCount=").append(logCount);
		sb.append('}');
		return sb.toString();
	}
}
