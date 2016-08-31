package za.org.grassroot.core.domain.geo;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.io.Serializable;
import java.time.LocalDate;
import java.util.Objects;

/**
 * This serves the purpose of natural composite primary key.
 */
@Embeddable
public class UserAndLocalDateKey implements Serializable {
	@Column(name = "user_uid", length = 50)
	private String userUid;

	@Column(name = "local_date")
	private LocalDate localDate;

	protected UserAndLocalDateKey() {
		// fo JPA
	}

	public UserAndLocalDateKey(String userUid, LocalDate localDate) {
		this.userUid = Objects.requireNonNull(userUid);
		this.localDate = Objects.requireNonNull(localDate);
	}

	public String getUserUid() {
		return userUid;
	}

	public LocalDate getLocalDate() {
		return localDate;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		UserAndLocalDateKey that = (UserAndLocalDateKey) o;

		if (!userUid.equals(that.userUid)) {
			return false;
		}

		return localDate.equals(that.localDate);
	}

	@Override
	public int hashCode() {
		int result = userUid.hashCode();
		result = 31 * result + localDate.hashCode();
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("{");
		sb.append("userUid='").append(userUid).append('\'');
		sb.append(", localDate=").append(localDate);
		sb.append('}');
		return sb.toString();
	}
}
