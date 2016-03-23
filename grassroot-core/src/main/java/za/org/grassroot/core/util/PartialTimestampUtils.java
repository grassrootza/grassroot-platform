package za.org.grassroot.core.util;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Objects;

public class PartialTimestampUtils {

	private PartialTimestampUtils() {
		// utility
	}

	public LocalTime getLocalTime(Timestamp timestamp) {
		if (timestamp == null) {
			return null;
		}
		return timestamp.toLocalDateTime().toLocalTime();
	}

	public Timestamp withLocalTime(Timestamp timestamp, LocalTime localTime) {
		Objects.requireNonNull(localTime);
		if (timestamp == null) {
			// if there is no event start date time set, set as current moment
			timestamp = Timestamp.from(Instant.now());
		}
		// take current event's start date time, and update time portion
		LocalDate localDate = timestamp.toLocalDateTime().toLocalDate();
		LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
		return Timestamp.valueOf(localDateTime);
	}

	public LocalDate getLocalDate(Timestamp timestamp) {
		if (timestamp == null) {
			return null;
		}
		return timestamp.toLocalDateTime().toLocalDate();
	}

	public Timestamp withLocalDate(Timestamp timestamp, LocalDate localDate) {
		Objects.requireNonNull(localDate);
		if (timestamp == null) {
			// if there is no event start date time set, set as current moment
			timestamp = Timestamp.from(Instant.now());
		}
		// take current event's start date time, and update date portion
		LocalTime localTime = timestamp.toLocalDateTime().toLocalTime();
		LocalDateTime localDateTime = LocalDateTime.of(localDate, localTime);
		return Timestamp.valueOf(localDateTime);
	}
}
