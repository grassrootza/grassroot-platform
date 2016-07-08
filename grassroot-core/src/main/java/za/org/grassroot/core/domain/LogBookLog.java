package za.org.grassroot.core.domain;

import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.LogBookLogType;

import javax.persistence.*;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book_log",
		indexes = {@Index(name = "idx_log_book_log_logbook_id", columnList = "log_book_id")})
public class LogBookLog implements ActionLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Basic
	@Column(name = "created_date_time", nullable = false, updatable = false)
	private Instant createdDateTime;

	@ManyToOne
	@JoinColumn(name = "log_book_id", nullable = false)
	private LogBook logBook;

	@Column(name = "message")
	private String message;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	@Enumerated(EnumType.STRING)
	@Column(name="type", nullable = false, length = 50)
 	private LogBookLogType type;

	private LogBookLog() {
		// for JPA only
	}

	public LogBookLog(LogBookLogType type, User user, LogBook logBook, String message) {
		this.type = Objects.requireNonNull(type);
		this.logBook = Objects.requireNonNull(logBook);
		this.message = message;
		this.user = user;
		this.createdDateTime = Instant.now();
	}

	public LogBookLogType getType() {
		return type;
	}

	public Long getId() {
		return id;
	}

	public Instant getCreatedDateTime() {
		return createdDateTime;
	}

	public LogBook getLogBook() {
		return logBook;
	}

	public User getUser() {
		return user;
	}

	public String getMessage() {
		return message;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("LogBookLog{");
		sb.append("type=").append(type);
		sb.append(", logBook=").append(logBook);
		sb.append(", user=").append(user);
		sb.append(", createdDateTime=").append(createdDateTime);
		sb.append(", id=").append(id);
		sb.append('}');
		return sb.toString();
	}
}
