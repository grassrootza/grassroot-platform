package za.org.grassroot.core.domain.geo;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Objects;

/**
 * This serves the purpose of natural composite primary key.
 */
@Embeddable
public class UserAndLocalTimeKey implements Serializable {
	@Column(name = "user_uid", length = 50)
	private String userUid;

	@Column(name = "local_time")
	private LocalDateTime localTime;

	protected UserAndLocalTimeKey() {
		// fo JPA
	}

	public UserAndLocalTimeKey(String userUid, LocalDateTime localTime) {
		this.userUid = Objects.requireNonNull(userUid);
		this.localTime = Objects.requireNonNull(localTime);
	}

	public String getUserUid() {
		return userUid;
	}

	public LocalDateTime getLocalTime() {
		return localTime;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		UserAndLocalTimeKey that = (UserAndLocalTimeKey) o;

		if (!userUid.equals(that.userUid)) {
			return false;
		}
		if (!localTime.equals(that.localTime)) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = userUid.hashCode();
		result = 31 * result + localTime.hashCode();
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		sb.append("userUid='").append(userUid).append('\'');
		sb.append(", localTime=").append(localTime);
		sb.append('}');
		return sb.toString();
	}
}
