package za.org.grassroot.services;

import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBookRequest;

import java.time.LocalDateTime;

public interface LogBookRequestBroker {

	LogBookRequest create(String userUid, String groupUid);

	LogBookRequest create(String userUid, String parentUid, JpaEntityType parentType, String message, LocalDateTime deadline,
						  int reminderMinutes, boolean replicateToSubGroups);

	void finish(String logBookUid);
}
