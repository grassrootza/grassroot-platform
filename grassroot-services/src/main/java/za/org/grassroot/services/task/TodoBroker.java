package za.org.grassroot.services.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.services.enums.TodoStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TodoBroker {

	Todo load(String todoUid);

	Todo update(Todo todo);

	/**
	 *
	 * @param userUid The uid of the user creating the action
	 * @param parentType The type of entity that is a parent to the action (usually a group, can be meeting or vote)
	 * @param parentUid The uid of the parent entity
	 * @param message The top-level content of the to-do
	 * @param actionByDate Passing a null value will trigger an immediate to-do, with no reminder active
	 * @param reminderMinutes The number of minutes in advance of the deadline that a reminder will be sent
	 * @param replicateToSubgroups Whether to cascade the action to subgroups
	 * @param assignedMemberUids THe set of members assigned to the action, if any
     * @return
     */
	Todo create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate,
				int reminderMinutes, boolean replicateToSubgroups, Set<String> assignedMemberUids);

	void assignMembers(String userUid, String todoUid, Set<String> assignMemberUids);

	void removeAssignedMembers(String userUid, String todoUid, Set<String> memberUids);

	void cancel(String userUid, String todoUid);

	Todo update(String userUid, String uid, String message, String description, LocalDateTime actionByDate, Integer reminderMinutes, Set<String> assignnedMemberUids);

	void updateSubject(String userUid, String todoUid, String newMessage);

	void updateDescription(String userUid, String todoUid, String description);

	void updateActionByDate(String userUid, String todoUid, LocalDateTime revisedActionByDate);

	/**
	 * Confirms completion by given user which should be a member of specified to-do.
	 * @param userUid member that is confirming completion
	 * @param todoUid action to-do ID
	 * @param confirmType whether the user responded that the to-do was completed, was not completed, or they don't know
	 * @param completionTime time of completion; can be null only in case when some other member previously set it for given to-do
	 * @return Whether the completion response pushed the to-do over the completion threshold
	 */
	boolean confirmCompletion(String userUid, String todoUid, TodoCompletionConfirmType confirmType, LocalDateTime completionTime);

	void sendScheduledReminder(String todoUid);

	/**
	 * Return a page of todos, for a given group or for all groups that a user is part of
	 * @param userUid The user initiating the query
	 * @param groupUid The group whose todos to return. If null, returns across all user's groups.
	 * @param pageRequest The page request (including sort instructions)
	 * @return
     */
	Page<Todo> fetchPageOfTodosForGroup(String userUid, String groupUid, Pageable pageRequest);

	List<Todo> fetchTodosForGroupByStatus(String groupUid, boolean futureTodosOnly, TodoStatus status);

	List<Todo> fetchTodosForGroupCreatedDuring(String groupUid, LocalDateTime createdAfter, LocalDateTime createdBefore);

	/*
	Check if a user needs to respond to a to-do (note : will not return to-dos where user has marked 'I don't know', to avoid infinite & annoying pinging
	 */
	Optional<Todo> fetchTodoForUserResponse(String userUid, boolean assignedTodosOnly);

	boolean userHasTodosForResponse(String userUid, boolean assignedTodosOnly);

	boolean userHasIncompleteActionsToView(String userUid);

	Page<Todo> fetchIncompleteActionsToView(String userUid, Pageable pageable);

	boolean userHasOldActionsToView(String userUid);

	/**
	 * Methods to handle "replicaed" todos (i.e., that cascade down subgroups)
	 */

	boolean hasReplicatedEntries(Todo todo);

	List<Group> retrieveGroupsFromTodos(List<Todo> todos);

	List<Todo> getAllReplicatedEntriesFromParent(Todo todo);
}
