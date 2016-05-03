package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.time.Instant;
import java.util.Date;
import java.util.Objects;

/**
 * Created by aakilomar on 12/3/15.
 */
@Entity
@Table(name = "log_book_log",
		indexes = {@Index(name = "idx_log_book_log_logbook_id", columnList = "logbook_id", unique = false)})

public class LogBookLog implements ActionLog {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@Basic
	@Column(name = "created_date_time", nullable = false, updatable = false)
	private Instant createdDateTime;

	@ManyToOne
	@JoinColumn(name = "logbook_id", nullable = false)
	private LogBook logBook;

	@Column(name = "message")
	private String message;

	@ManyToOne
	@JoinColumn(name = "user_id")
	private User user;

	private LogBookLog() {
		// for JPA only
	}

	public LogBookLog(User user, LogBook logBook, String message) {
		this.logBook = Objects.requireNonNull(logBook);
		this.message = message;
		this.user = user;
		this.createdDateTime = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
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
		return "LogBookLog{" +
				"id=" + id +
				", createdDateTime=" + createdDateTime +
				", message='" + message + '\'' +
				'}';
	}
}
