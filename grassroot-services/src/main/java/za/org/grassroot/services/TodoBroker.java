package za.org.grassroot.services;

import org.springframework.data.domain.Page;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.services.enums.TodoStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TodoBroker {

	Todo load(String todoUid);

	Todo update(Todo todo);

	Todo create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate,
				int reminderMinutes, boolean replicateToSubgroups, Set<String> assignedMemberUids);

	void assignMembers(String userUid, String todoUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String todoUid, Set<String> memberUids);

	void cancel(String userUid, String todoUid);

	Todo update(String userUid, String uid, String message, String description, LocalDateTime actionByDate, Integer reminderMinutes, Set<String> assignnedMemberUids);

	/**
	 * Confirms completion by given user which should be a member of specified todo.
	 * @param userUid member that is confirming completion
	 * @param todoUid action to-do ID
	 * @param completionTime time of completion; can be null only in case when some other member previously set it for given todo
	 */
	boolean confirmCompletion(String userUid, String todoUid, LocalDateTime completionTime);

	void sendScheduledReminder(String todoUid);

	/**
	 * Return a page of todos, for a given group or for all groups that a user is part of
	 * @param userUid The user initiating the query
	 * @param groupUid The group whose todos to return. If null, returns across all user's groups.
	 * @param entriesComplete Whether to return complete or incomplete entries
	 * @param pageNumber The page number
	 * @param pageSize The page size
     * @return
     */
	Page<Todo> fetchPageOfTodosForGroup(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize);

	List<Todo> fetchTodosForGroupByStatus(String groupUid, boolean futureTodosOnly, TodoStatus status);

	List<Todo> fetchTodosForGroupCreatedDuring(String groupUid, LocalDateTime createdAfter, LocalDateTime createdBefore);

	/*
	Check if a user needs to respond to a to-do
	 */

	// todo: we need some sort of logic here for not showing users the same todo over and over (plus handle assigned vs parent)
	Optional<Todo> fetchTodoForUserResponse(String userUid, boolean assignedTodosOnly);

	boolean userHasTodosForResponse(String userUid, boolean assignedTodosOnly);

	/**
	 * Methods to handle "replicaed" todos (i.e., that cascade down subgroups)
	 */

	boolean hasReplicatedEntries(Todo todo);

	List<Group> retrieveGroupsFromTodos(List<Todo> todos);

	List<Todo> getAllReplicatedEntriesFromParent(Todo todo);

	Todo getParentTodoEntry(Todo todo);
}
