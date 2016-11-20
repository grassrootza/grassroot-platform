package za.org.grassroot.services.task;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.TodoRequest;

import java.time.LocalDateTime;

public interface TodoRequestBroker {

	TodoRequest load(String requestUid);

	TodoRequest create(String userUid, String groupUid);

	TodoRequest create(String userUid, String parentUid, JpaEntityType parentType, String message, LocalDateTime deadline,
					   int reminderMinutes, boolean replicateToSubGroups);

	void updateMessage(String userUid, String requestUid, String message);

	void updateDueDate(String userUid, String requestUid, LocalDateTime dueDate);

	void finish(String todoUid);
}
