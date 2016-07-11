package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.services.enums.LogBookStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

public interface LogBookBroker {
	LogBook load(String logBookUid);

	LogBook create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate,
				   int reminderMinutes, boolean replicateToSubgroups, Set<String> assignedMemberUids);

	void assignMembers(String userUid, String logBookUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String logBookUid, Set<String> memberUids);

	/**
	 * Confirms completion by given user which should be a member of specified logbook.
	 * @param userUid member that is confirming completion
	 * @param logBookUid logbook ID
	 * @param completionTime time of completion; can be null only in case when some other member previously set it for given logbook
	 */
	boolean confirmCompletion(String userUid, String logBookUid, LocalDateTime completionTime);

	void sendScheduledReminder(String logBookUid);

	/**
	 * Return a page of logbooks, for a given group or for all groups that a user is part of
	 * @param userUid The user initiating the query
	 * @param groupUid The group whose logbooks to return. If null, returns across all user's groups.
	 * @param entriesComplete Whether to return complete or incomplete entries
	 * @param pageNumber The page number
	 * @param pageSize The page size
     * @return
     */
	Page<LogBook> retrieveGroupLogBooks(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize);

	List<Group> retrieveGroupsFromLogBooks(List<LogBook> logBooks);

	// todo: we need some sort of logic here for not showing users the same logbook over and over
	LogBook fetchLogBookForUserResponse(String userUid, long daysInPast, boolean assignedLogBooksOnly);

	LogBook update(String userUid, String uid, String message, LocalDateTime actionByDate, int reminderMinutes, Set<String> assignnedMemberUids);


}
