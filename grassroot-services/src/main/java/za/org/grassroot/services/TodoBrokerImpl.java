package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.TodoInfoNotification;
import za.org.grassroot.core.domain.notification.TodoReminderNotification;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.enums.TodoStatus;
import za.org.grassroot.services.exception.EventStartTimeNotInFutureException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service
public class TodoBrokerImpl implements TodoBroker {

	private static final Logger logger = LoggerFactory.getLogger(TodoBrokerImpl.class);

	@Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
	private double COMPLETION_PERCENTAGE_BOUNDARY;

	@Value("${grassroot.todos.number.reminders:2}")
	private int DEFAULT_NUMBER_REMINDERS;

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

	@Override
	@Transactional
	public Todo create(String userUid, JpaEntityType parentType, String parentUid, String message, LocalDateTime actionByDate, int reminderMinutes,
					   boolean replicateToSubgroups, Set<String> assignedMemberUids) {

		Objects.requireNonNull(userUid);
		Objects.requireNonNull(parentType);
		Objects.requireNonNull(parentUid);
		Objects.requireNonNull(message);
		Objects.requireNonNull(actionByDate);
		Objects.requireNonNull(assignedMemberUids);

		User user = userRepository.findOneByUid(userUid);
		TodoContainer parent = uidIdentifiableRepository.findOneByUid(TodoContainer.class, parentType, parentUid);

		logger.info("Creating new log book: userUid={}, parentType={}, parentUid={}, message={}, actionByDate={}, reminderMinutes={}, assignedMemberUids={}, replicateToSubgroups={}",
				userUid, parentType, parentUid, message, actionByDate, reminderMinutes, assignedMemberUids, replicateToSubgroups);

		Instant convertedActionByDate = convertToSystemTime(actionByDate, getSAST());

		if (convertedActionByDate.isBefore(Instant.now())) {
			throw new EventStartTimeNotInFutureException("Error! Attempt to create todo with due date in the past");
		}

		Todo todo = new Todo(user, parent, message, convertedActionByDate, reminderMinutes, null, DEFAULT_NUMBER_REMINDERS, true);

		if (!assignedMemberUids.isEmpty()) {
			assignedMemberUids.add(userUid);
		}

		todo.assignMembers(assignedMemberUids);
		todo = todoRepository.save(todo);

		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		TodoLog todoLog = new TodoLog(TodoLogType.CREATED, user, todo, null);
		bundle.addLog(todoLog);

		Set<Notification> notifications = constructTodoNotifications(todo, todoLog);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		// replication means this could get _very_ expensive, so we probably want to move this to async once it starts being used
		if (replicateToSubgroups && parent.getJpaEntityType().equals(JpaEntityType.GROUP)) {
			replicateTodoToSubgroups(user, todo, actionByDate);
		}

		return todo;
	}

	private Set<Notification> constructTodoNotifications(Todo todo, TodoLog todoLog) {
		Set<Notification> notifications = new HashSet<>();
		// the "recorded" notification gets sent to all users in parent, not just assigned (to re-evaluate in future)
		for (User member : todo.getParent().getMembers()) {
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

		if (assignMemberUids != null && !assignMemberUids.isEmpty()) {
			assignMemberUids.add(userUid);
		}

		todo.assignMembers(assignMemberUids);
	}

	@Override
	@Transactional
	public void removeAssignedMembers(String userUid, String todoUid, Set<String> memberUids) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		if (!todo.getCreatedByUser().equals(user)) {
			throw new AccessDeniedException("Only user who created todo can change assignment");
		}

		todo.removeAssignedMembers(memberUids);
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
	public boolean confirmCompletion(String userUid, String todoUid, LocalDateTime completionTime) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(todoUid);

		User user = userRepository.findOneByUid(userUid);
		Todo todo = todoRepository.findOneByUid(todoUid);

		logger.info("Confirming completion todo={}, completion time={}, user={}", todo, completionTime, user);

		Instant completionInstant = completionTime == null ? null : convertToSystemTime(completionTime, getSAST());
		boolean confirmationRegistered = todo.addCompletionConfirmation(user, completionInstant);
		if (!confirmationRegistered) {
			// should error be raised when member already registered completions !?
			logger.info("Completion confirmation already exists for member {} and log book {}", user, todo);
		}
		return confirmationRegistered;
	}


	@Override
	@Transactional
	public void sendScheduledReminder(String todoUid) {
		Objects.requireNonNull(todoUid);

		Todo todo = todoRepository.findOneByUid(todoUid);
		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
		TodoLog todoLog = new TodoLog(TodoLogType.REMINDER_SENT, null, todo, null);

		Set<User> members = todo.isAllGroupMembersAssigned() ? todo.getAncestorGroup().getMembers() : todo.getAssignedMembers();
		for (User member : members) {
			String message = messageAssemblingService.createTodoReminderMessage(member, todo);
			Notification notification = new TodoReminderNotification(member, message, todoLog);
			bundle.addNotification(notification);
		}

		// we only want to include log if there are some notifications
		if (!bundle.getNotifications().isEmpty()) {
			bundle.addLog(todoLog);
		}

		// reduce number of reminders to send and calculate new reminder minutes
		// to reduce SMS / notification traffic, reducing this to just one day
		todo.setNumberOfRemindersLeftToSend(todo.getNumberOfRemindersLeftToSend() - 1);
		if (todo.getNumberOfRemindersLeftToSend() > 0) {
			// i.e., advance it by 5 days (~ working week).
			todo.setReminderMinutes(todo.getReminderMinutes() - DateTimeUtil.numberOfMinutesForDays(5));
			todo.calculateScheduledReminderTime();
		} else {
			todo.setReminderActive(false);
		}

		logsAndNotificationsBroker.storeBundle(bundle);
	}

	@Override
	@Transactional(readOnly = true)
	public Page<Todo> retrieveGroupTodos(String userUid, String groupUid, boolean entriesComplete, int pageNumber, int pageSize) {
		Objects.requireNonNull(userUid);

		Page<Todo> page;
		Pageable pageable = new PageRequest(pageNumber, pageSize);
		User user = userRepository.findOneByUid(userUid);



		if (groupUid != null) {
			Group group = groupRepository.findOneByUid(groupUid);
			permissionBroker.validateGroupPermission(user, group, null); // make sure user is part of group
			if (entriesComplete) {
				page = todoRepository.findByParentGroupAndCompletionPercentageGreaterThanEqualAndCancelledFalseOrderByActionByDateDesc(group, COMPLETION_PERCENTAGE_BOUNDARY, pageable);
			} else {
				page = todoRepository.findByParentGroupAndCompletionPercentageLessThanAndCancelledFalseOrderByActionByDateDesc(group, COMPLETION_PERCENTAGE_BOUNDARY, pageable);
			}
		} else {
			if (entriesComplete) {
				page = todoRepository.findByParentGroupMembershipsUserAndCompletionPercentageGreaterThanEqualAndCancelledFalseOrderByActionByDateDesc(user, COMPLETION_PERCENTAGE_BOUNDARY, pageable);
			} else {
				page = todoRepository.findByParentGroupMembershipsUserAndCompletionPercentageLessThanOrderByActionByDateDesc(user, COMPLETION_PERCENTAGE_BOUNDARY, pageable);
			}
		}

		return page;
	}

	@Override
	public List<Todo> getTodosInPeriod(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
		Sort sort = new Sort(Sort.Direction.ASC, "createdDateTime");
		Instant start = convertToSystemTime(periodStart, getSAST());
		Instant end = convertToSystemTime(periodEnd, getSAST());
		return todoRepository.findByParentGroupAndCreatedDateTimeBetween(group, start, end, sort);
	}

	@Override
	public List<Group> retrieveGroupsFromTodos(List<Todo> todos) {
		return todos.stream()
				.filter(todo -> todo.getParent().getJpaEntityType().equals(JpaEntityType.GROUP))
				.map(todo -> (Group) todo.getParent())
				.collect(Collectors.toList());
	}

	public List<Todo> loadGroupTodos(String groupUid, boolean futureTodosOnly, TodoStatus status) {
		Objects.requireNonNull(groupUid);

		Group group = groupRepository.findOneByUid(groupUid);
		Instant start = futureTodosOnly ? Instant.now() : DateTimeUtil.getEarliestInstant();

		switch (status) {
			case COMPLETE:
				return todoRepository.findByParentGroupAndCompletionPercentageGreaterThanEqualAndActionByDateGreaterThanAndCancelledFalse(group, COMPLETION_PERCENTAGE_BOUNDARY, start);
			case INCOMPLETE:
				return todoRepository.findByParentGroupAndCompletionPercentageLessThanAndActionByDateGreaterThanAndCancelledFalse(group, COMPLETION_PERCENTAGE_BOUNDARY, start);
			case BOTH:
				return todoRepository.findByParentGroupAndActionByDateGreaterThanAndCancelledFalse(group, start);
			default:
				return todoRepository.findByParentGroupAndActionByDateGreaterThanAndCancelledFalse(group, start);
		}
	}

	@Override
	@Transactional(readOnly = true)
	public Todo fetchTodoForUserResponse(String userUid, long daysInPast, boolean assignedTodosOnly) {
		Optional<Todo> lbToReturn;
		User user = userRepository.findOneByUid(userUid);
		Instant end = Instant.now();
		Instant start = Instant.now().minus(daysInPast, ChronoUnit.DAYS);
		Sort sort = new Sort(Sort.Direction.ASC, "actionByDate"); // so the most overdue come up first

		List<Todo> userLbs = !assignedTodosOnly ?
				todoRepository.findByParentGroupMembershipsUserAndActionByDateBetweenAndCompletionPercentageLessThanAndCancelledFalse(user, start, end, COMPLETION_PERCENTAGE_BOUNDARY, sort)
				: todoRepository.findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageLessThan(user, start, end, COMPLETION_PERCENTAGE_BOUNDARY, sort);

		lbToReturn = userLbs
				.stream()
				.filter(t -> !t.isCompletedBy(user))
				.findFirst();

		return lbToReturn.isPresent() ? lbToReturn.get() : null;
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

		Set<Notification> notifications = constructTodoNotifications(todo, todoLog);
		bundle.addNotifications(notifications);

		logsAndNotificationsBroker.storeBundle(bundle);

		return todo;
	}

	@Override
	@Transactional(readOnly = true)
	public boolean hasReplicatedEntries(Todo todo) {
		return todoRepository.countReplicatedEntries(todo.getAncestorGroup(), todo.getMessage(), todo.getCreatedDateTime()) != 0;
	}

	@Override
	@Transactional(readOnly = true)
	public List<Todo> getAllReplicatedEntriesFromParent(Todo todo) {
		return todoRepository.findByReplicatedGroupAndMessageAndActionByDateOrderByParentGroupIdAsc(todo.getAncestorGroup(), todo.getMessage(),
				todo.getActionByDate());
	}

	@Override
	@Transactional(readOnly = true)
	public Todo getParentTodoEntry(Todo todo) {
		Group parentTodoGroup = todo.getReplicatedGroup();
		if (parentTodoGroup == null) {
			return null;
		}
		else return todoRepository.findByParentGroupAndMessageAndCreatedDateTime(parentTodoGroup, todo.getMessage(),
				todo.getCreatedDateTime()).get(0);
	}

}
