package za.org.grassroot.core.domain.task;

import za.org.grassroot.core.domain.UidIdentifiable;

import java.util.Set;

public interface TodoContainer extends UidIdentifiable {
	Set<Todo> getTodos();
	Integer getTodoReminderMinutes();
	EventReminderType getReminderType();
}
