package za.org.grassroot.core.domain.geo;

import javax.persistence.EmbeddedId;
import javax.persistence.Entity;
import javax.persistence.Table;
import java.util.Objects;

@Entity
@Table(name = "prev_period_user_location")
public class PreviousPeriodUserLocation {
	@EmbeddedId
	private UserAndLocalTimeKey key;

	private GeoLocation location;

	private PreviousPeriodUserLocation() {
		// for JPA
	}

	public PreviousPeriodUserLocation(UserAndLocalTimeKey key, GeoLocation location) {
		this.key = Objects.requireNonNull(key);
		this.location = Objects.requireNonNull(location);
	}

	public UserAndLocalTimeKey getKey() {
		return key;
	}

	public GeoLocation getLocation() {
		return location;
	}

	public String toString() {
		final StringBuilder sb = new StringBuilder("PreviousPeriodUserLocation{");
		sb.append("key=").append(key);
		sb.append(", location=").append(location);
		sb.append('}');
		return sb.toString();
	}
}
