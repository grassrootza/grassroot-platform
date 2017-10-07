package za.org.grassroot.core.domain.notification;

import za.org.grassroot.core.domain.ActionLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.enums.NotificationDetailedType;
import za.org.grassroot.core.enums.NotificationType;

import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;
import javax.persistence.MappedSuperclass;

@MappedSuperclass
public abstract class TodoNotification extends Notification {
	@ManyToOne
	@JoinColumn(name = "action_todo_id")
	private Todo todo;

	@Override
	public NotificationType getNotificationType() {
		return NotificationType.TODO;
	}

	@Override
	public abstract NotificationDetailedType getNotificationDetailedType();


	protected TodoNotification() {
		// for JPA
	}

	@Override
	protected void appendToString(StringBuilder sb) {
		sb.append(", todo=").append(todo);
	}

	protected TodoNotification(User target, String message, TodoLog todoLog) {
		this(target, message, todoLog, todoLog.getTodo());
	}

	protected TodoNotification(User target, String message, ActionLog actionLog, Todo todo) {
        super(target, message, actionLog);
        this.todo = todo;
	}

	public Todo getTodo() {
		return todo;
	}
}
