package za.org.grassroot.webapp.controller.ussd;

import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

public interface UssdTodoService {
	USSDMenu respondToTodo(User user, Todo todo);
}
