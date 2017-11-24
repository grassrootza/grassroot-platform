package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;

import java.time.LocalDateTime;

public interface TodoRequestBroker {

	TodoRequest load(String requestUid);

	TodoRequest create(String userUid, TodoType todoType);

	void updateGroup(String userUid, String requestUid, String groupUid);

	void updateMessage(String userUid, String requestUid, String message);

	void updateDueDate(String userUid, String requestUid, LocalDateTime dueDate);

	void updateResponseTag(String userUid, String requestUid, String responseTag);

	void finish(String todoUid);
}
