package za.org.grassroot.core.domain;

import javax.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "action_todo_completion_confirmation",
		uniqueConstraints = @UniqueConstraint(name = "uk_compl_confirmation_action_todo_member", columnNames = {"log_book_id", "member_id"}))
public class TodoCompletionConfirmation {
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_log_book_compl_confirm_member"))
	private User member;

	@ManyToOne(optional = false)
	@JoinColumn(name = "log_book_id", nullable = false, foreignKey = @ForeignKey(name = "fk_log_book_compl_confirm_log_book"))
	private Todo todo;

	@Column(name = "completion_time")
	private Instant completionTime;

	private TodoCompletionConfirmation() {
		// for JPA
	}

	public TodoCompletionConfirmation(Todo todo, User member, Instant completionTime) {
		this.todo = todo;
		this.member = member;
		this.completionTime = completionTime;
	}

	public Long getId() {
		return id;
	}

	public User getMember() {
		return member;
	}

	public Todo getTodo() {
		return todo;
	}


	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (o == null || getClass() != o.getClass()) {
			return false;
		}

		TodoCompletionConfirmation that = (TodoCompletionConfirmation) o;

		if (!member.equals(that.member)) {
			return false;
		}
		if (!todo.equals(that.todo)) {
			return false;
		}

		return true;
	}

	@Override
	public int hashCode() {
		int result = member.hashCode();
		result = 31 * result + todo.hashCode();
		return result;
	}

	@Override
	public String toString() {
		final StringBuilder sb = new StringBuilder("TodoCompletionConfirmation{");
		sb.append("member=").append(member.getUsername());
		sb.append(", todo=").append(todo);
		sb.append(", completionTime=").append(completionTime);
		sb.append('}');
		return sb.toString();
	}
}
