package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.TodoInfoNotification;
import za.org.grassroot.core.domain.notification.TodoReminderNotification;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.TodoAssignmentRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.TodoSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.exception.ResponseNotAllowedException;
import za.org.grassroot.services.exception.TodoDeadlineNotInFutureException;
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TodoBrokerImpl implements TodoBroker {

    private final TodoRepository todoRepository;
    private final TodoAssignmentRepository todoAssignmentRepository;
    private final UserRepository userRepository;
    private final UidIdentifiableRepository uidIdentifiableRepository;

    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private final MessageAssemblingService messageService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TodoBrokerImpl(TodoRepository todoRepository, TodoAssignmentRepository todoAssignmentRepository, UserRepository userRepository, UidIdentifiableRepository uidIdentifiableRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, MessageAssemblingService messageService, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.todoAssignmentRepository = todoAssignmentRepository;
        this.userRepository = userRepository;
        this.uidIdentifiableRepository = uidIdentifiableRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    @Transactional(readOnly = true)
    public Todo load(String todoUid) {
        Objects.requireNonNull(todoUid);
        return todoRepository.findOneByUid(todoUid);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTimeChangedDTO> fetchTodosWithTimeChanged(Set<String> todoUids) {
        Objects.requireNonNull(todoUids);
        return todoRepository.fetchTodosWithTimeChanged(todoUids);
    }

    @Override
    @Transactional
    public String create(TodoHelper todoHelper) {
        todoHelper.validateMinimumFields();

        User user = userRepository.findOneByUid(todoHelper.getUserUid());
        TodoContainer parent = uidIdentifiableRepository.findOneByUid(TodoContainer.class,
                todoHelper.getParentType(), todoHelper.getParentUid());

        if (todoHelper.getParentType().equals(JpaEntityType.GROUP)) {
            Todo possibleDuplicate = checkForDuplicate(user, (Group) parent, todoHelper);
            if (possibleDuplicate != null) {
                return possibleDuplicate.getUid();
            }
        }

        validateUserCanCreate(user, parent.getThisOrAncestorGroup());

        if (todoHelper.getDueDateTime().isBefore(Instant.now())) {
            throw new TodoDeadlineNotInFutureException();
        }

        Todo todo = new Todo(user, parent, todoHelper.getTodoType(), todoHelper.getSubject(), todoHelper.getDueDateTime());
        todo = todoRepository.save(todo);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        TodoLog todoLog = new TodoLog(TodoLogType.CREATED, user, todo, null);
        bundle.addLog(todoLog);
        bundle.addNotifications(wireUpTodoForType(todo, todoHelper, todoLog));
        logsAndNotificationsBroker.storeBundle(bundle);

        return todo.getUid();
    }

    private Todo checkForDuplicate(User creator, Group group, TodoHelper helper) {
        Instant intervalStart = helper.getDueDateTime().minus(180, ChronoUnit.SECONDS);
        Instant intervalEnd = helper.getDueDateTime().plus(180, ChronoUnit.SECONDS);

        return todoRepository.findOne(TodoSpecifications.checkForDuplicates(intervalStart, intervalEnd, creator, group,
                helper.getSubject()));
    }

    private Set<Notification> wireUpTodoForType(Todo todo, TodoHelper todoHelper, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        if (todoHelper.isInformationTodo()) {
            todo.setResponseTag(todoHelper.getResponseTag());
        }

        if (todoHelper.getAssignedMemberUids() == null || todoHelper.getAssignedMemberUids().isEmpty()) {
            setAllParentMembersAssigned(todo, shouldAssignedUsersRespond(todo.getType()));
        } else {
            setAssignedMembers(todo, todoHelper.getAssignedMemberUids(), shouldAssignedUsersRespond(todo.getType()));
        }

        if (todoHelper.getConfirmingMemberUids() != null && !todoHelper.getConfirmingMemberUids().isEmpty()) {
            setConfirmingMembers(todo, todoHelper.getConfirmingMemberUids());
        }

        Set<User> assignedUsers = todo.getAssignedUsers();
        Set<User> confirmingUsers = todo.getConfirmingUsers();

        Set<User> assignedNonConfirmingUsers = new HashSet<>(assignedUsers);
        assignedNonConfirmingUsers.removeAll(confirmingUsers);

        assignedNonConfirmingUsers.forEach(user -> notifications.add(generateTodoAssignedNotification(todo, user, todoLog)));
        confirmingUsers.forEach(user -> notifications.add(generateNotificationForConfirmingUsers(todo, user, todoLog)));

        return notifications;
    }

    private boolean shouldAssignedUsersRespond(TodoType type) {
        return TodoType.INFORMATION_REQUIRED.equals(type) || TodoType.VOLUNTEERS_NEEDED.equals(type);
    }

    // todo : as below, proper validation on types, combinations, etc (e.g., responses only within group)
    // todo : handle properly where users are both assigned and confirming
    private void setAllParentMembersAssigned(Todo todo, boolean shouldRespond) {
        todo.setAssignments(todo.getParent().getMembers().stream()
                .map(u -> new TodoAssignment(todo, u, true, false, shouldRespond && !u.equals(todo.getCreatedByUser()))).collect(Collectors.toSet()));
    }

    private void setAssignedMembers(Todo todo, Set<String> assignedMemberUids, boolean shouldRespond) {
        List<User> users = userRepository.findByUidIn(assignedMemberUids);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, true, false, shouldRespond && !u.equals(todo.getCreatedByUser())))
                .collect(Collectors.toSet()));
    }

    private void setConfirmingMembers(Todo todo, Set<String> confirmingMemberUids) {
        List<User> users = userRepository.findByUidIn(confirmingMemberUids);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, false, true, true)).collect(Collectors.toSet()));
    }

    private Notification generateTodoAssignedNotification(Todo todo, User target, TodoLog todoLog) {
        String message = messageService.createTodoAssignedMessage(target, todo);
        return new TodoInfoNotification(target, message, todoLog);
    }

    private Notification generateNotificationForConfirmingUsers(Todo todo, User target, TodoLog todoLog) {
        String message = messageService.createTodoConfirmerMessage(target, todo);
        return new TodoInfoNotification(target, message, todoLog);
    }

    @Override
    @Transactional
    public void cancel(String userUid, String todoUid, String reason) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setCancelled(true);
        // todo : send out notifications
        createAndStoreTodoLog(user, todo, TodoLogType.CANCELLED, reason);
    }

    @Override
    @Transactional
    public void extend(String userUid, String todoUid, Instant newDueDateTime) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        if (newDueDateTime.isBefore(Instant.now())) {
            throw new TodoDeadlineNotInFutureException();
        }
        validateUserCanModify(user, todo);
        todo.setActionByDate(newDueDateTime);
        createAndStoreTodoLog(user, todo, TodoLogType.EXTENDED, newDueDateTime.toString());
    }

    @Override
    @Transactional
    public void updateSubject(String userUid, String todoUid, String newSubject) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setMessage(newSubject);
        createAndStoreTodoLog(user, todo, TodoLogType.CHANGED, "Changed description: " + newSubject);
    }

    @Override
    @Transactional
    public void recordValidation(String userUid, String todoUid, String notes, Set<String> taskImageUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getType().equals(TodoType.VALIDATION_REQUIRED)) {
            throw new TodoTypeMismatchException();
        }

        User user = userRepository.findOneByUid(userUid);
        validateUserCanConfirm(user, todo);

        if(todo.getAncestorGroup().getMembership(user) == null){
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ALTER_TODO);
        }

        todo.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now());
        createAndStoreTodoLog(user, todo, TodoLogType.RESPONDED, "todo confirmed");
    }

    @Override
    @Transactional(readOnly = true)
    public Todo checkForTodoNeedingResponse(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        // last in first out (at present - makes most sense if user is responding to something)
        Pageable limit = new PageRequest(0, 1, new Sort(Sort.Direction.DESC, "createdDateTime"));
        Page<Todo> result = todoRepository.findAll(TodoSpecifications.todosForUserResponse(user), limit);
        return result.getNumberOfElements() == 0 ? null : result.getContent().get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserRespond(String userUid, String todoUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        return todoAssignmentRepository.count(TodoSpecifications.userAssignmentCanRespond(user, todo)) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserViewResponses(String userUid, String todoUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        return todo.getCreatedByUser().equals(user) || todoAssignmentRepository.count(TodoSpecifications.userAssignment(user, todo)) != 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserModify(String userUid, String todoUid) {
        // for the moment, trivial, but we are likely to alter in future
        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        return todo.getCreatedByUser().equals(user);
    }

    @Override
    @Transactional
    public void recordResponse(String userUid, String todoUid, String response, boolean confirmRecorded) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(response);

        Todo todo = todoRepository.findOneByUid(todoUid);

        // todo : enforce assignment only within group members when create

        User user = userRepository.findOneByUid(userUid);
        if(todo.getAncestorGroup().getMembership(user) == null){
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ALTER_TODO);
        }
        TodoAssignment todoAssignment = validateUserCanRespondToTodo(user, todo);

        todoAssignment.setResponseText(response);
        todoAssignment.setResponseTime(Instant.now());
        todoAssignment.setHasResponded(true);

        TodoLog todoLog = new TodoLog(TodoLogType.RESPONDED, user, todo, response);
        Set<Notification> notifications = new HashSet<>();
        switch (todo.getType()) {
            case INFORMATION_REQUIRED:
                notifications.addAll(recordInformationResponse(todoAssignment, response, todoLog, confirmRecorded));
                break;
            case VALIDATION_REQUIRED:
                notifications.addAll(processValidation(todoAssignment, response, todoLog));
                break;
            case VOLUNTEERS_NEEDED:
                notifications.addAll(notifyCreatorOfVolunteer(todoAssignment, response, todoLog));
                break;
        }

        storeLogAndNotifications(todoLog, notifications);
    }

    @Override
    @Transactional
    public void updateTodoCompleted(String userUid, String todoUid, boolean completed) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        validateUserCanModify(user, todo);

        // to make sure reminders are turned off (reminder query should filter out, but just to be sure)
        // todo  : extend to above as well
        todo.setNextNotificationTime(null);
        todo.setReminderActive(false);

        todo.setCompleted(completed);
    }

    @Override
    @Transactional
    public void addAssignments(String addingUserUid, String todoUid, Set<String> addedMemberUids) {
        Objects.requireNonNull(addingUserUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(addedMemberUids);

        User user = userRepository.findOneByUid(addingUserUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        validateUserCanModify(user, todo);

        Set<String> priorMembers = todo.getAssignments().stream().map(a -> a.getUser().getUid()).collect(Collectors.toSet());

        // todo : log (esp if some drop out on filter above) and validate this step etc
        addedMemberUids.removeAll(priorMembers);
        List<User> newUsers = userRepository.findByUidIn(addedMemberUids);
        todo.addAssignments(newUsers.stream().map(u -> new TodoAssignment(todo, u, true, false,
                shouldAssignedUsersRespond(todo.getType()))).collect(Collectors.toSet()));

        if (!addedMemberUids.isEmpty()) {
            TodoLog newLog = new TodoLog(TodoLogType.ASSIGNED_ADDED, user, todo, "Assigned " + addedMemberUids.size() +
                    " new members to todo");
            Set<Notification> notifications = newUsers.stream()
                    .map(t -> generateTodoAssignedNotification(todo, t, newLog)).collect(Collectors.toSet());
            logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), notifications));
        }
    }

    @Override
    @Transactional
    public void addValidators(String addingUserUid, String todoUid, Set<String> validatingMemberUids) {
        Objects.requireNonNull(addingUserUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(validatingMemberUids);

        User user = userRepository.findOneByUid(addingUserUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        validateUserCanModify(user, todo);

        // todo : bunch of validation

        Set<String> existingAssignments = new HashSet<>();
        todo.getAssignments().stream()
                .filter(a -> validatingMemberUids.contains(a.getUser().getUid()))
                .forEach(a -> {
                    a.setValidator(true);
                    existingAssignments.add(a.getUser().getUid());
                });

        Set<String> newAssignmentUids = new HashSet<>(validatingMemberUids);
        newAssignmentUids.removeAll(existingAssignments);

        List<User> newValidators = userRepository.findByUidIn(newAssignmentUids);
        todo.addAssignments(newValidators.stream().map(u -> new TodoAssignment(todo, u, false, true, true)).collect(Collectors.toSet()));

        if (!validatingMemberUids.isEmpty()) {
            TodoLog newLog = new TodoLog(TodoLogType.VALIDATORS_ADDED, user, todo, "Added " + validatingMemberUids.size() +
                    " new validators to todo");
            Set<Notification> notifications = newValidators.stream() // watch this, may want to also do the switched users...
                    .map(t -> generateNotificationForConfirmingUsers(todo, t, newLog)).collect(Collectors.toSet());
            logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), notifications));
        }
    }

    @Override
    @Transactional
    public void removeUsers(String removingUserUid, String todoUid, Set<String> memberUidsToRemove) {
        Objects.requireNonNull(removingUserUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(memberUidsToRemove);

        User user = userRepository.findOneByUid(removingUserUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        validateUserCanModify(user, todo);

        List<User> users = userRepository.findByUidIn(memberUidsToRemove);
        // to preserve records, we set them to non-assigned, rather than delete
        todoAssignmentRepository.findAll(TodoSpecifications.userInAndForTodo(new HashSet<>(users), todo))
                .forEach(ta -> {
                    ta.setAssignedAction(false);
                    ta.setValidator(false);
                });

        if (!memberUidsToRemove.isEmpty()) {
            TodoLog newLog = new TodoLog(TodoLogType.ASSIGNED_REMOVED, user, todo, "Removed " + memberUidsToRemove.size() + " from todo");
            logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(newLog), Collections.emptySet()));
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> fetchTodosForUser(String userUid, boolean forceIncludeCreated, boolean limitToNeedingResponse, Instant intervalStart, Instant intervalEnd, Sort sort) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        Specifications<Todo> specs = limitToNeedingResponse ?
                TodoSpecifications.todosForUserResponse(user) :
                Specifications.where(TodoSpecifications.userPartOfParent(user));

        if (forceIncludeCreated) {
            specs = specs.or((root, query, cb) -> cb.equal(root.get(Todo_.createdByUser), user));
        }

        if (intervalStart != null) {
            specs = specs.and((root, query, cb) -> cb.greaterThan(root.get(Todo_.actionByDate), intervalStart));
        }
        if (intervalEnd != null) {
            specs = specs.and((root, query, cb) -> cb.lessThan(root.get(Todo_.actionByDate), intervalEnd));
        }

        specs = specs.and((root, query, cb) -> cb.isFalse(root.get(Todo_.cancelled)));
        return sort == null ? todoRepository.findAll(specs) : todoRepository.findAll(specs, sort);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<Todo> fetchPageOfTodosForUser(String userUid, boolean createdOnly, boolean openOnly, Pageable pageRequest) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return todoRepository.findAll(TodoSpecifications.todosForUserResponse(user), pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> fetchTodosForGroup(String userUid, String groupUid, boolean limitToNeedingResponse, boolean limitToIncomplete,
                                         Instant start, Instant end, Sort sort) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = uidIdentifiableRepository.findOneByUid(Group.class, JpaEntityType.GROUP, groupUid);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS);

        Specifications<Todo> specs;
        if (limitToNeedingResponse) {
            specs = TodoSpecifications.todosForUserResponse(user).and(TodoSpecifications.hasGroupAsParent(group));
        } else {
            specs = Specifications.where(TodoSpecifications.hasGroupAsParent(group));
        }

        specs = specs.and((root, query, cb) -> cb.isFalse(root.get(Todo_.cancelled)));
        if (limitToIncomplete) {
            specs = specs.and((root, query, cb) -> cb.isFalse(root.get(Todo_.completed)));
        }

        if (start != null) {
            specs = specs.and((root, query, cb) -> cb.greaterThan(root.get(Todo_.actionByDate), start));
        }
        if (end != null) {
            specs = specs.and((root, query, cb) -> cb.lessThan(root.get(Todo_.actionByDate), end));
        }

        return sort != null ? todoRepository.findAll(specs, sort) : todoRepository.findAll(specs);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> searchUserTodos(String userUid, String searchString) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(searchString);

        User user = userRepository.findOneByUid(userUid);
        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(searchString, true, false);
        return todoRepository.findByParentGroupMembershipsUserAndMessageSearchTerm(user.getId(), tsQuery);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTimeChangedDTO> fetchGroupTodosWithTimeChanged(String groupUid) {
        Objects.requireNonNull(groupUid);
        Group group = uidIdentifiableRepository.findOneByUid(Group.class, JpaEntityType.GROUP, groupUid);
        return todoRepository.fetchGroupTodosWithTimeChanged(group);
    }

    @Override
    @Transactional(readOnly = true)
    public TodoAssignment fetchUserTodoDetails(String userUid, String todoUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);
        return todoAssignmentRepository.findOne(TodoSpecifications.userAssignment(user, todo));
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskTimeChangedDTO> fetchUserTodosWithTimeChanged(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return todoRepository.fetchTodosWithTimeChangedForUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TodoAssignment> fetchAssignedUserResponses(String userUid, String todoUid, boolean respondedOnly, boolean assignedOnly, boolean witnessOnly) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userRepository.findOneByUid(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        if (!todo.getCreatedByUser().equals(user) || todoAssignmentRepository.count(TodoSpecifications.userAssignment(user, todo)) == 0) {
            throw new AccessDeniedException("Error, only creating or assigned user can see todo details");
        }

        Specifications<TodoAssignment> specs = Specifications.where((root, query, cb) -> cb.equal(root.get(TodoAssignment_.todo), todo));

        if (respondedOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.hasResponded)));
        }

        if (assignedOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.assignedAction)));
        }

        if (witnessOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.validator)));
        }

        List<Sort.Order> orders = Arrays.asList(new Sort.Order("hasResponded"), new Sort.Order(Sort.Direction.DESC, "responseTime"));
        return todoAssignmentRepository.findAll(specs, new Sort(orders));
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
                    String message = messageService.createTodoReminderMessage(member, todo);
                    Notification notification = new TodoReminderNotification(member, message, todoLog);
                    bundle.addNotification(notification);
                });

        // we only want to include log if there are some notifications
        if (!bundle.getNotifications().isEmpty()) {
            bundle.addLog(todoLog);
        }

        if (todo.isRecurring()) {
            todo.setNextNotificationTime(Instant.now().plus(Duration.ofMillis(todo.getRecurInterval())));
        } else {
            todo.setReminderActive(false);
        }

        logsAndNotificationsBroker.storeBundle(bundle);

    }

    private Set<Notification> recordInformationResponse(TodoAssignment assignment, String response, TodoLog todoLog, boolean sendConfirmation) {
        Set<Notification> notifications = new HashSet<>();

        // todo: handle possible duplication
        Group group = assignment.getTodo().getAncestorGroup();
        Membership membership = group.getMembership(assignment.getUser());

        final String memberTag = assignment.getTodo().getResponseTag() + ":" + response;
        log.info("adding tag to member: {}", memberTag);
        membership.addTag(memberTag);

        if (sendConfirmation) {
            final String message = messageService.createTodoInfoConfirmationMessage(assignment);
            notifications.add(new TodoInfoNotification(assignment.getUser(), message, todoLog));
        }

        return notifications;
    }

    private Set<Notification> processValidation(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : restrict notifications to only if on paid account (else just do at deadline), and put in proper message
        // todo : as below, handle different kinds of response better
        if(assignment.getTodo().getAncestorGroup().isPaidFor()) {
            if ("yes".equalsIgnoreCase(response)) {
                notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(),
                        "someone validated", todoLog));
            }else if("no".equalsIgnoreCase(response)){
                notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(),
                        "someone invalidated", todoLog));
            }
        }
        return notifications;
    }

    private Set<Notification> notifyCreatorOfVolunteer(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : consider notifying if volunteer responds "no" (if on paid account ...)
        // todo : handle non-predictable text entry (use NLU)
        if ("yes".equalsIgnoreCase(response)) {
            // todo : consider sending messages to other organizers
            final String message = messageService.createTodoVolunteerReceivedMessage(assignment.getTodo().getCreatedByUser(), assignment);
            notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(), message, todoLog));
        }

        if(assignment.getTodo().getAncestorGroup().isPaidFor() && "no".equalsIgnoreCase(response)){
            final String message = messageService
                    .createTodoVolunteerReceivedMessage(assignment.getTodo().getCreatedByUser(), assignment);
            notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(), message, todoLog));
        }
        return notifications;
    }

    private void createAndStoreTodoLog(User user, Todo todo, TodoLogType todoLogType, String description) {
        TodoLog todoLog = new TodoLog(todoLogType, user, todo, description);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(todoLog);
        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);
    }

    private void storeLogAndNotifications(TodoLog todoLog, Set<Notification> notifications) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(todoLog);
        bundle.addNotifications(notifications);
        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);
    }

    private void validateUserCanCreate(User user, Group group) {
        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        }
    }

    private void validateUserCanModify(User user, Todo todo) {
        if (!todo.getCreatedByUser().equals(user)) {
            permissionBroker.validateGroupPermission(user, todo.getAncestorGroup(),
                    Permission.GROUP_PERMISSION_ALTER_TODO);
        }else{
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ALTER_TODO);
        }
    }

    private void validateUserCanConfirm(User user, Todo todo) {
        if (!todo.getConfirmingUsers().contains(user)) {
            throw new ResponseNotAllowedException();
        }
    }

    private TodoAssignment validateUserCanRespondToTodo(User user, Todo todo) {
        TodoAssignment todoAssignment = todoAssignmentRepository.findOne(
                TodoSpecifications.userAssignment(user, todo));
        if (todoAssignment == null) {
            throw new ResponseNotAllowedException();
        }
        if (TodoType.VALIDATION_REQUIRED.equals(todo.getType()) && !todoAssignment.isValidator() && todo.getAncestorGroup().getMembership(user) != null) {
            throw new ResponseNotAllowedException();
        }
        return todoAssignment;
    }
}
