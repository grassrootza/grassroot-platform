package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;

public interface LogBookBroker {
	LogBook create(String userUid, String groupUid, boolean replicateToSubgroups, String message, Timestamp actionByDate,
					   String assignedToUserUid, int reminderMinutes);

	void complete(String logBookUid, Timestamp completionTime, String completedByUserUid);
}
