package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "log_book_completion_confirmation",
		uniqueConstraints = @UniqueConstraint(name = "uk_compl_confirmation_log_book_member", columnNames = {"log_book_id", "member_id"}))
public class LogBookCompletionConfirmation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_log_book_compl_confirm_member"))
	private User member;

	@ManyToOne(optional = false)
	@JoinColumn(name = "log_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_log_book_compl_confirm_log_book"))
	private LogBook logBook;

	@Column(name = "completion_time")
	private Instant completionTime;

	private LogBookCompletionConfirmation() {
		// for JPA
	}

	public LogBookCompletionConfirmation(LogBook logBook, User member, Instant completionTime) {
		this.logBook = logBook;
		this.member = member;
		this.completionTime = completionTime;
	}

	public Long getId() {
		return id;
	}

	public User getMember() {
		return member;
	}

	public LogBook getLogBook() {
		return logBook;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		LogBookCompletionConfirmation that = (LogBookCompletionConfirmation) o;

		if (!member.equals(that.member)) {
			return false;
		}
		if (!logBook.equals(that.logBook)) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = member.hashCode();
		result = 31 * result + logBook.hashCode();
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("LogBookCompletionConfirmation{");
		sb.append("member=").append(member.getUsername());
		sb.append(", logBook=").append(logBook);
		sb.append(", completionTime=").append(completionTime);
		sb.append('}');
		return sb.toString();
	}
}
