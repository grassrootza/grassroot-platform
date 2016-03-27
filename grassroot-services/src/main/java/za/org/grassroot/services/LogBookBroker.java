package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface LogBookBroker {
	LogBook load(String logBookUid);

	LogBook create(String userUid, JpaEntityType parentType, String parentUid, String message, Timestamp actionByDate, int reminderMinutes,
				   boolean replicateToSubgroups, Set<String> assignedMemberUids);

	void assignMembers(String userUid, String logBookUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String logBookUid, Set<String> memberUids);

	void complete(String logBookUid, LocalDateTime completionTime, String completedByUserUid);

	Page<LogBook> retrieveGroupLogBooks(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize);

	List<Group> retrieveGroupsFromLogBooks(List<LogBook> logBooks);
}
