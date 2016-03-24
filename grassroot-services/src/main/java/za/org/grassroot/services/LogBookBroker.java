package za.org.grassroot.services;

import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.util.Set;

public interface LogBookBroker {
	LogBook create(String userUid, String groupUid, String message, Timestamp actionByDate, int reminderMinutes,
				   boolean replicateToSubgroups, Set<String> assignedMemberUids);

	void assignMembers(String userUid, String logBookUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String logBookUid, Set<String> memberUids);

	void complete(String logBookUid, Timestamp completionTime, String completedByUserUid);
}
