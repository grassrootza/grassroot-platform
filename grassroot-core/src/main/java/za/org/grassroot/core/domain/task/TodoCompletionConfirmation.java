package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;

import javax.persistence.*;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "action_todo_completion_confirmation",
		uniqueConstraints = @UniqueConstraint(name = "uk_compl_confirmation_action_todo_member", columnNames = {"action_todo_id", "member_id"}))
public class TodoCompletionConfirmation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	@Column(name = "id", nullable = false)
	private Long id;

	@ManyToOne(optional = false)
	@JoinColumn(name = "member_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_todo_compl_confirm_member"))
	private User member;

	@ManyToOne(optional = false)
	@JoinColumn(name = "action_todo_id", nullable = false, foreignKey = @ForeignKey(name = "fk_action_todo_compl_confirm_action_todo"))
	private Todo todo;

	@Column(name = "creation_time", nullable = false, updatable = false) // i.e., when the user made the response
	private Instant creationTime;

	@Column(name = "completion_time") // i.e., when the to-do was completed
	private Instant completionTime;

	@Enumerated(EnumType.STRING)
	@Column(name = "confirmation_type", nullable = false)
	private TodoCompletionConfirmType confirmType;

	private TodoCompletionConfirmation() {
		// for JPA
	}

	public TodoCompletionConfirmation(Todo todo, User member, TodoCompletionConfirmType confirmType, Instant completionTime) {
		Objects.requireNonNull(todo);
		Objects.requireNonNull(member);
		Objects.requireNonNull(confirmType);

		this.todo = todo;
		this.member = member;
		this.completionTime = completionTime;
		this.confirmType = confirmType;
		this.creationTime = Instant.now();
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

	public Instant getCreationTime() { return creationTime; }

	public Instant getCompletionTime() { return completionTime; }

	public TodoCompletionConfirmType getConfirmType() { return confirmType; }

	public void setCompletionTime(Instant completionTime) { this.completionTime = completionTime; }

	public void setConfirmType(TodoCompletionConfirmType confirmType) { this.confirmType = confirmType; }

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

		return todo.equals(that.todo);
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
