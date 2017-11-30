package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.UIDGenerator;

import javax.persistence.Entity;
import javax.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "action_todo_request")
public class TodoRequest extends AbstractTodoEntity {

	private TodoRequest() {
		// for JPA
	}

	public TodoRequest(User creatingUser, TodoType type) {
		this.uid = UIDGenerator.generateId();
		this.createdDateTime = Instant.now();
		this.createdByUser = creatingUser;
		this.type = type;
	}

	public static TodoRequest makeEmpty(User createdByUser, TodoContainer parent) {
		TodoRequest request = new TodoRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		request.setParent(parent);
		return request;
	}

	public static TodoRequest makeEmpty(User createdByUser) {
		TodoRequest request = new TodoRequest();
		request.uid = UIDGenerator.generateId();
		request.createdByUser = createdByUser;
		return request;
	}

	@Override
	public String toString() {
		return "TodoRequest{" +
				"type=" + type +
				", message='" + message + '\'' +
				", responseTag='" + responseTag + '\'' +
				", actionByDate=" + actionByDate +
				'}';
	}
}
