package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.services.enums.LogBookStatus;

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
	 * Marks a logbook entry as complete. Records that the first passed user marked the entry as complete, and, optionally,
	 * records who completed the logbook and when.
	 *
	 * @param userUid The user recording the logbook as complete
	 * @param logBookUid The uid of the logbook entry that was marked complete
	 * @param completionTime When the logbook was completed (in user's local timezone). If null, marked as now.
	 * @param completedByUserUid The user who completed the action. If null, marked as assigned member, or left unrecorded.
     * @return True, if the logbook state was changed (i.e., was "not complete"), false, if it was already marked
     */
	boolean complete(String userUid, String logBookUid, LocalDateTime completionTime, String completedByUserUid);

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

	List<LogBook> loadGroupLogBooks(String groupUid, boolean futureLogBooksOnly, LogBookStatus status);

	List<LogBook> loadUserLogBooks(String userUid, boolean assignedLogBooksOnly, boolean futureLogBooksOnly, LogBookStatus status);

	// todo: we need some sort of logic here for not showing users the same logbook over and over
	LogBook fetchLogBookForUserResponse(String userUid, long daysInPast, boolean assignedLogBooksOnly);


}
