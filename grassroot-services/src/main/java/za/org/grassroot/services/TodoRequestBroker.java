package za.org.grassroot.services;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBookRequest;

import java.time.LocalDateTime;
import java.util.Set;

public interface TodoRequestBroker {

	LogBookRequest load(String requestUid);

	LogBookRequest create(String userUid, String groupUid);

	LogBookRequest create(String userUid, String parentUid, JpaEntityType parentType, String message, LocalDateTime deadline,
						  int reminderMinutes, boolean replicateToSubGroups);

	void updateMessage(String userUid, String requestUid, String message);

	void updateDueDate(String userUid, String requestUid, LocalDateTime dueDate);

	void finish(String logBookUid);
}
