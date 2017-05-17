package za.org.grassroot.services.task;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.TodoLog;
import za.org.grassroot.core.domain.notification.TodoInfoNotification;
import za.org.grassroot.core.domain.notification.TodoReminderNotification;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.enums.TodoStatus;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.specifications.TodoSpecifications;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.services.specifications.TodoSpecifications.*;

@Service
public class TodoBrokerImpl implements TodoBroker {

	private static final Logger logger = LoggerFactory.getLogger(TodoBrokerImpl.class);

	@Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
	private double COMPLETION_PERCENTAGE_BOUNDARY;

	@Value("${grassroot.todos.number.reminders:1}")
	private int DEFAULT_NUMBER_REMINDERS;

	@Value("${grassroot.todos.days_over.prompt:7}")
	private int DAYS_PAST_FOR_TODO_CHECKING;

	@Value("${grassroot.todos.days_after.reminder:3}")
	private int DAYS_AFTER_FOR_REMINDER;

	@Autowired
	private UserRepository userRepository;
	@Autowired
	private UidIdentifiableRepository uidIdentifiableRepository;
	@Autowired
	private GroupRepository groupRepository;
	@Autowired
	private TodoRepository todoRepository;
	@Autowired
	private MessageAssemblingService messageAssemblingService;

	@Autowired
	private LogsAndNotificationsBroker logsAndNotificationsBroker;
	@Autowired
	private PermissionBroker permissionBroker;
	@Autowired
	private AccountGroupBroker accountGroupBroker;

	@Override
	@Transactional(readOnly = true)
	public Todo load(String todoUid) {
		return todoRepository.findOneByUid(todoUid);
	}

	@Override
	@Transactional
	public Todo update(Todo todo) {
		return todoRepository.save(todo);
	}

	private Todo checkForDuplicate(User user, Group group, String message, Instant actionByDate) {
		Instant intervalStart = actionByDate.minus(180, ChronoUnit.SECONDS);;
		Instant intervalEnd = actionByDate.plus(180, ChronoUnit.SECONDS);

		return todoRepository.findOne(Specifications.where(actionByDateBetween(intervalStart, intervalEnd))
				.and(TodoSpecifications.messageIs(message))
				.and(TodoSpecifications.hasGroupAsParent(group))
				.and(TodoSpecifications.createdByUser(user)));
	}

	@Override
	@Transactional
	public Todo create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate, int reminderMinutes,
					   boolean replicateToSubgroups, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentType);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(assignedMemberUids);

		User user = userRepository.findOneByUid(userUid);
		TodoContainer parent = uidIdentifiableRepository.findOneByUid(TodoContainer.class, parentType, parentUid);

		Group ancestorGroup = parent.getThisOrAncestorGroup();
		if (accountGroupBroker.numberTodosLeftForGroup(ancestorGroup.getUid()) < 1) {
			throw new AccountLimitExceededException();
		}

		boolean isInstantAction = actionByDate == null;
		Instant convertedActionByDate = isInstantAction ? Instant.now().plus(5, ChronoUnit.MINUTES) : convertToSystemTime(actionByDate, getSAST());

		logger.info("Creating new log book: userUid={}, parentType={}, parentUid={}, message={}, isInstant={}, actionByDate={}, reminderMinutes={}, assignedMemberUids={}, replicateToSubgroups={}",
				userUid, parentType, parentUid, message, isInstantAction, actionByDate, reminderMinutes, assignedMemberUids, replicateToSubgroups);

		if (convertedActionByDate.isBefore(Instant.now())) {
			throw new EventStartTimeNotInFutureException("Error! Attempt to create todo with due date in the past");
		}

		if (parentType.equals(JpaEntityType.GROUP)) {
			Todo possibleDuplicate = checkForDuplicate(user, (Group) parent, message, convertedActionByDate);
			if (possibleDuplicate != null) {
				logger.info("Found a duplicate! Returning");
				return possibleDuplicate;
			}
		}

		Todo todo = new Todo(user, parent, message, convertedActionByDate, reminderMinutes, null,
				isInstantAction ? 0 : DEFAULT_NUMBER_REMINDERS, !isInstantAction);

		if (!assignedMemberUids.isEmpty()) {
			assignedMemberUids.add(userUid);
		}

		todo.assignMembers(assignedMemberUids);
		todo = todoRepository.save(todo);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		TodoLog todoLog = new TodoLog(TodoLogType.CREATED, user, todo, null);
		bundle.addLog(todoLog);

		Set<Notification> notifications = constructTodoNotifications(todo, todoLog, todo.getMembers());
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		// replication means this could get _very_ expensive, so we probably want to move this to async once it starts being used
		if (replicateToSubgroups && parent.getJpaEntityType().equals(JpaEntityType.GROUP)) {
			replicateTodoToSubgroups(user, todo, actionByDate);
		}

		return todo;
	}

	private Set<Notification> constructTodoNotifications(Todo todo, TodoLog todoLog, Set<User> membersToNotify) {
		Set<Notification> notifications = new HashSet<>();
		for (User member : membersToNotify) {
			String message = messageAssemblingService.createTodoRecordedNotificationMessage(member, todo);
			Notification notification = new TodoInfoNotification(member, message, todoLog);
			notifications.add(notification);
		}
		return notifications;
	}

	private void replicateTodoToSubgroups(User user, Todo todo, LocalDateTime actionByDate) {
		Group group = todo.getAncestorGroup();
		// note: getGroupAndSubGroups is a much faster method (a recursive query) than getSubGroups, hence use it and just skip parent
		List<Group> groupAndSubGroups = groupRepository.findGroupAndSubGroupsById(group.getId());
		for (Group subGroup : groupAndSubGroups) {
			if (!group.equals(subGroup)) {
				create(user.getUid(), JpaEntityType.GROUP, subGroup.getUid(), todo.getMessage(), actionByDate,
					   todo.getReminderMinutes(), true, new HashSet<>());
			}
		}
	}

	@Override
	@Transactional
	public void assignMembers(String userUid, String todoUid, Set<String> assignMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		if (!todo.getCreatedByUser().equals(user)) { // in future, possibly change to any assigned user, maybe
			throw new AccessDeniedException("Only user who created todo can change assignment");
		}

		Set<User> priorMembers = todo.getMembers();

		if (assignMemberUids != null && !assignMemberUids.isEmpty()) {
			assignMemberUids.add(userUid);
		}

		todo.assignMembers(assignMemberUids);

		Set<User> addedMembers = todo.getMembers();
		addedMembers.removeAll(priorMembers);

		if (!addedMembers.isEmpty()) {
			TodoLog newLog = new TodoLog(TodoLogType.ASSIGNED_ADDED, user, todo, "Assigned " + addedMembers.size() +
					" new members to todo");
			Set<Notification> notifications = constructTodoNotifications(todo, newLog, addedMembers);
			logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), notifications));
		}
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String todoUid, Set<String> memberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		Set<User> priorMembers = todo.getMembers();

		if (!todo.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Only user who created todo can change assignment");
		}

		todo.removeAssignedMembers(memberUids);

		priorMembers.removeAll(todo.getMembers());
		if (!priorMembers.isEmpty()) {
			TodoLog newLog = new TodoLog(TodoLogType.ASSIGNED_REMOVED, user, todo, "Removed " + priorMembers.size() + " from todo");
			logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), Collections.emptySet()));
		}
	}

	@Override
	@Transactional
	public void cancel(String userUid, String todoUid) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		if (!todo.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only the creating user can cancel a todo");
		}

		todo.setCancelled(true);
	}

	@Override
	@Transactional
	public boolean confirmCompletion(String userUid, String todoUid, TodoCompletionConfirmType confirmType, LocalDateTime completionTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);
		Objects.requireNonNull(confirmType);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		logger.info("Confirming completion type={}, todo={}, completion time={}, user={}", todo, confirmType, completionTime, user);

		Instant completionInstant = completionTime == null ? null : convertToSystemTime(completionTime, getSAST());
		boolean tippedThreshold = todo.addCompletionConfirmation(user, confirmType, completionInstant, COMPLETION_PERCENTAGE_BOUNDARY);

		if (tippedThreshold || (todo.getCreatedByUser().equals(user) && confirmType.equals(TodoCompletionConfirmType.COMPLETED))) {
			// to make sure reminders are turned off (reminder query should filter out, but just to be sure)
			todo.setNumberOfRemindersLeftToSend(0);
			todo.setReminderActive(false);
			return true;
		} else {
			return false;
		}
	}


	@Override
	@Transactional
	public void sendScheduledReminder(String todoUid) {
		Objects.requireNonNull(todoUid);

		Todo todo = todoRepository.findOneByUid(todoUid);
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		TodoLog todoLog = new TodoLog(TodoLogType.REMINDER_SENT, null, todo, null);

		Set<User> members = todo.isAllGroupMembersAssigned() ?
				todo.getAncestorGroup().getMembers() : todo.getAssignedMembers();

		members.stream().filter(m -> !todo.isCompletionConfirmedByMember(m))
				.forEach(member -> {
					String message = messageAssemblingService.createTodoReminderMessage(member, todo);
					Notification notification = new TodoReminderNotification(member, message, todoLog);
					bundle.addNotification(notification);
				});

		// we only want to include log if there are some notifications
		if (!bundle.getNotifications().isEmpty()) {
			bundle.addLog(todoLog);
		}

		// reduce number of reminders to send and calculate new reminder minutes
		todo.setNumberOfRemindersLeftToSend(todo.getNumberOfRemindersLeftToSend() - 1);
		if (todo.getNumberOfRemindersLeftToSend() > 0) {
			todo.setReminderMinutes(todo.getReminderMinutes() - DateTimeUtil.numberOfMinutesForDays(DAYS_AFTER_FOR_REMINDER));
			todo.calculateScheduledReminderTime();
		} else {
			todo.setReminderActive(false);
		}

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Todo> fetchPageOfTodosForGroup(String userUid, String groupUid, Pageable pageRequest) {
		Objects.requireNonNull(userUid);
		User user = userRepository.findOneByUid(userUid);
		Group group = groupRepository.findOneByUid(groupUid);
		permissionBroker.validateGroupPermission(user, group, null); // make sure user is part of group
		Specifications<Todo> specifications = Specifications
				.where(notCancelled())
				.and(hasGroupAsParent(group))
				.and(completionConfirmsAbove(COMPLETION_PERCENTAGE_BOUNDARY, false));
		return todoRepository.findAll(specifications, pageRequest);
	}

	@Override
	@Transactional(readOnly = true)
	public List<Todo> fetchTodosForGroupCreatedDuring(String groupUid, LocalDateTime periodStart, LocalDateTime periodEnd) {
		Group group = groupRepository.findOneByUid(groupUid);
		Sort sort = new Sort(Sort.Direction.ASC, "createdDateTime");

		Instant start = convertToSystemTime(periodStart, getSAST());
		Instant end = convertToSystemTime(periodEnd, getSAST());

		return todoRepository.findAll(Specifications.where(hasGroupAsParent(group)).and(createdDateBetween(start, end)), sort);
	}

	@Override
	public List<Group> retrieveGroupsFromTodos(List<Todo> todos) {
		return todos.stream()
				.filter(todo -> todo.getParent().getJpaEntityType().equals(JpaEntityType.GROUP))
				.map(todo -> (Group) todo.getParent())
				.collect(Collectors.toList());
	}

	@Override
	@Transactional(readOnly = true)
	public List<Todo> fetchTodosForGroupByStatus(String groupUid, boolean futureTodosOnly, TodoStatus status) {
		Objects.requireNonNull(groupUid);

		Group group = groupRepository.findOneByUid(groupUid);
		Instant start = futureTodosOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();

		Specifications<Todo> specifications = Specifications.where(TodoSpecifications.notCancelled())
				.and(TodoSpecifications.actionByDateAfter(start))
				.and(hasGroupAsParent(group));

		logger.info("Looking with status {}, and boundary {}", status, COMPLETION_PERCENTAGE_BOUNDARY);

		if (TodoStatus.COMPLETE.equals(status)) {
			specifications = specifications.and(TodoSpecifications.completionConfirmsAbove(COMPLETION_PERCENTAGE_BOUNDARY, true));
		} else if (TodoStatus.INCOMPLETE.equals(status)) {
			specifications = specifications.and(TodoSpecifications.completionConfirmsBelow(COMPLETION_PERCENTAGE_BOUNDARY, false));
		}

		return todoRepository.findAll(specifications);
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<Todo> fetchTodoForUserResponse(String userUid, boolean assignedTodosOnly) {
		final User user = userRepository.findOneByUid(userUid);
		final Sort sort = new Sort(Sort.Direction.ASC, "actionByDate"); // get the most overdue
		final List<Todo> result = todoRepository.findAll(incompleteTodoSpecifications(user, DAYS_PAST_FOR_TODO_CHECKING, false), sort);
		if (result == null || result.isEmpty()) {
			return Optional.empty();
		} else {
			return result.stream()
					.filter(t -> !assignedTodosOnly || t.isAllGroupMembersAssigned() || t.getAssignedMembers().contains(user))
					.filter(t -> !t.hasUserResponded(user)) // might be able to include in specifications, but could be overloading for small gain
					.findFirst();
		}
	}

	@Override
	@Transactional(readOnly = true)
	public boolean userHasTodosForResponse(String userUid, boolean assignedTodosOnly) {
		return fetchTodoForUserResponse(userUid, assignedTodosOnly).isPresent();
	}

	@Override
	@Transactional(readOnly = true)
	public boolean userHasIncompleteActionsToView(String userUid) {
		User user = userRepository.findOneByUid(userUid);
		return todoRepository.count(incompleteTodoSpecifications(user, 90, true)) > 0;
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Todo> fetchIncompleteActionsToView(String userUid, Pageable pageable) {
		// important: we assume that the pageable has sort instructions embedded in it
		User user = userRepository.findOneByUid(userUid);
		return todoRepository.findAll(incompleteTodoSpecifications(user, 90, true), pageable);
	}

	private Specifications<Todo> incompleteTodoSpecifications(User user, long daysPastToCheck, boolean dueInFutureToo) {
		// what we want: todos on groups that the user is part of, with action by date in a defined interval, where the action by date is in the
		// range and the to-do has not been marked completed by the creator of the to-do
		return Specifications
				.where(notCancelled())
				.and(userPartOfGroup(user))
				.and(actionByDateBetween(Instant.now().minus(daysPastToCheck, ChronoUnit.DAYS),
						dueInFutureToo ? DateTimeUtil.getVeryLongAwayInstant() : Instant.now()))
				.and(completionConfirmsBelow(COMPLETION_PERCENTAGE_BOUNDARY, false))
				.and(todoNotConfirmedByCreator());
	}

	@Override
	@Transactional(readOnly = true)
	public boolean userHasOldActionsToView(String userUid) {
		User user = userRepository.findOneByUid(userUid);
		// note : we do not use the completion threshold because then actions which are under threshold but over 90 days old
		// might get lost (since the view incomplete functionality is time limited) -- and this controls whether menu appears, so need to be cautious
		return todoRepository.count(Specifications.where(notCancelled()).and(userPartOfGroup(user))) > 0;
	}

	@Override
	@Transactional
	public Todo update(String userUid, String taskUid, String message, String description, LocalDateTime actionByDate, Integer reminderMinutes, Set<String> assignedMemberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(taskUid);

		Todo todo = todoRepository.findOneByUid(taskUid);
		User user = userRepository.findOneByUid(userUid);

		if (!StringUtils.isEmpty(message)) {
			todo.setMessage(message);
		}

		if (actionByDate != null) {
			Instant convertedActionByDate = convertToSystemTime(actionByDate, getSAST());
			if (!convertedActionByDate.equals(todo.getActionByDate())) {
				todo.setActionByDate(convertedActionByDate);
			}
		}

		if (reminderMinutes != null) {
			todo.setReminderMinutes(reminderMinutes);
			todo.calculateScheduledReminderTime();
		}

		if(assignedMemberUids != null && !assignedMemberUids.isEmpty()){
			assignedMemberUids.add(userUid);
			todo.assignMembers(assignedMemberUids);
		}

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		TodoLog todoLog = new TodoLog(TodoLogType.CHANGED, user, todo, null);
		bundle.addLog(todoLog);

		Set<Notification> notifications = constructTodoNotifications(todo, todoLog, null);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		return todo;
	}

	@Override
	@Transactional
	public void updateSubject(String userUid, String todoUid, String newMessage) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);
		Objects.requireNonNull(newMessage);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		validateUserCanModify(user, todo);
		todo.setMessage(newMessage);
	}

    @Override
	@Transactional
    public void updateDescription(String userUid, String todoUid, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        validateUserCanModify(user, todo);

        todo.setDescription(description);
    }

    @Override
	@Transactional
	public void updateActionByDate(String userUid, String todoUid, LocalDateTime revisedActionByDate) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);
		Objects.requireNonNull(revisedActionByDate);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		validateUserCanModify(user, todo);
		todo.setActionByDate(convertToSystemTime(revisedActionByDate, getSAST()));
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasReplicatedEntries(Todo todo) {
		return todoRepository.countBySourceTodo(todo) != 0;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Todo> getAllReplicatedEntriesFromParent(Todo todo) {
		return todoRepository.findBySourceTodo(todo);
	}

	private void validateUserCanModify(User user, Todo todo) {
		if (!todo.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Error! Only the user who recorded the action can change its details");
		}
	}
}