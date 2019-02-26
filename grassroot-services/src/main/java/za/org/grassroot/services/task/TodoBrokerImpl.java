package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.TodoCancelledNotification;
import za.org.grassroot.core.domain.notification.TodoInfoNotification;
import za.org.grassroot.core.domain.notification.TodoReminderNotification;
import za.org.grassroot.core.domain.notification.UserLanguageNotification;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.TodoAssignmentRepository;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.specifications.TodoSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountFeaturesBroker;
import za.org.grassroot.services.exception.*;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.services.user.UserManagementService;
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
    private final UserManagementService userService;
    private final UidIdentifiableRepository uidIdentifiableRepository;

    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private final MessageAssemblingService messageService;
    private final ApplicationEventPublisher eventPublisher;

    private TaskImageBroker taskImageBroker;
    private PasswordTokenService tokenService;
    private GraphBroker graphBroker;
    private AccountFeaturesBroker accountFeaturesBroker;

    @Autowired
    public TodoBrokerImpl(TodoRepository todoRepository, TodoAssignmentRepository todoAssignmentRepository, UserManagementService userService, UidIdentifiableRepository uidIdentifiableRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, MessageAssemblingService messageService, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.todoAssignmentRepository = todoAssignmentRepository;
        this.userService = userService;
        this.uidIdentifiableRepository = uidIdentifiableRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.messageService = messageService;
        this.eventPublisher = eventPublisher;
    }

    @Autowired // we may want to make these optional in the future ...
    public void setTaskImageBroker(TaskImageBroker taskImageBroker) {
        this.taskImageBroker = taskImageBroker;
    }

    @Autowired
    public void setTokenService(PasswordTokenService tokenService) {
        this.tokenService = tokenService;
    }

    @Autowired
    public void setAccountFeaturesBroker(AccountFeaturesBroker accountFeaturesBroker) {
        this.accountFeaturesBroker = accountFeaturesBroker;
    }

    @Autowired(required = false)
    public void setGraphBroker(GraphBroker graphBroker) {
        this.graphBroker = graphBroker;
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
        validateTodoInFuture(todoHelper);

        User user = userService.load(todoHelper.getUserUid());
        TodoContainer parent = uidIdentifiableRepository.findOneByUid(TodoContainer.class, todoHelper.getParentType(), todoHelper.getParentUid());

        if (parent instanceof Group) {
            Todo possibleDuplicate = checkForDuplicate(user, (Group) parent, todoHelper);
            if (possibleDuplicate != null) {
                return possibleDuplicate.getUid();
            }

            if (accountFeaturesBroker.numberTodosLeftForGroup(parent.getUid()) < 0) {
                throw new AccountLimitExceededException();
            }
        }

        validateUserCanCreate(user, parent.getThisOrAncestorGroup());

        Todo todo = new Todo(user, parent, todoHelper.getTodoType(), todoHelper.getSubject(), todoHelper.getDueDateTime());
        todo = todoRepository.save(todo);

        if (todoHelper.getAssignedMemberUids() == null || todoHelper.getAssignedMemberUids().isEmpty()) {
            setAllParentMembersAssigned(todo, shouldAssignedUsersRespond(todo.getType()));
        } else {
            setAssignedMembers(todo, todoHelper.getAssignedMemberUids(), shouldAssignedUsersRespond(todo.getType()));
        }

        todo = setTodoImage(todo, user, todoHelper);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        TodoLog todoLog = new TodoLog(TodoLogType.CREATED, user, todo, null);
        bundle.addLog(todoLog);
        Set<Notification> notifications = wireUpTodoForType(todo, todoHelper, todoLog);
        bundle.addNotifications(notifications);
        logsAndNotificationsBroker.storeBundle(bundle);

        generateResponseTokens(todo, notifications);

        log.info("and now we are done with todo creation ... adding to graph, if enabled");

        if (graphBroker != null) {
            eventPublisher.publishEvent(placeTodoInGraph(todo));
        }

        return todo.getUid();
    }

    private AfterTxCommitTask placeTodoInGraph(Todo todo) {
        return () -> {
            List<String> assignedUids = todo.getMembers().stream().map(User::getUid).collect(Collectors.toList());
            graphBroker.addTaskToGraph(todo.getUid(), todo.getTaskType(), assignedUids);
            graphBroker.annotateTask(todo.getUid(), todo.getTaskType(), null, null, true);
        };
    }

    private Todo checkForDuplicate(User creator, Group group, TodoHelper helper) {
        Instant intervalStart = helper.getDueDateTime().minus(180, ChronoUnit.SECONDS);
        Instant intervalEnd = helper.getDueDateTime().plus(180, ChronoUnit.SECONDS);

        return todoRepository.findOne(TodoSpecifications.checkForDuplicates(intervalStart, intervalEnd, creator, group,
                helper.getSubject())).orElse(null);
    }

    private Todo setTodoImage(Todo todo, User user, TodoHelper todoHelper) {
        if (todoHelper.getMediaFileUids() != null && !todoHelper.getMediaFileUids().isEmpty()) {
            taskImageBroker.recordImageForTask(user.getUid(), todo.getUid(), TaskType.TODO, todoHelper.getMediaFileUids(),
                    null, TodoLogType.IMAGE_AT_CREATION);
            todo.setImageUrl(taskImageBroker.getShortUrl(todoHelper.getMediaFileUids().get(0)));
        }
        return todo;
    }

    private Set<Notification> wireUpTodoForType(Todo todo, TodoHelper todoHelper, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        if (todoHelper.isInformationTodo()) {
            todo.setResponseTag(todoHelper.getResponseTag());
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

    private void generateResponseTokens(Todo todo, Set<Notification> notifications) {
        Set<String> emailMemberUids = notifications.stream().map(Notification::getTarget).filter(User::areNotificationsByEmail)
                .map(User::getUid).collect(Collectors.toSet());
        if (!emailMemberUids.isEmpty()) {
            tokenService.generateResponseTokens(emailMemberUids, todo.getAncestorGroup().getUid(), todo.getUid());
        }
    }

    private boolean shouldAssignedUsersRespond(TodoType type) {
        return TodoType.INFORMATION_REQUIRED.equals(type) || TodoType.VOLUNTEERS_NEEDED.equals(type);
    }

    private void setAllParentMembersAssigned(Todo todo, boolean shouldRespond) {
        todo.setAssignments(todo.getParent().getMembers().stream()
                .map(u -> new TodoAssignment(todo, u, true, false, shouldRespond && !u.equals(todo.getCreatedByUser()))).collect(Collectors.toSet()));
    }

    private void setAssignedMembers(Todo todo, Set<String> assignedMemberUids, boolean shouldRespond) {
        List<User> users = userService.load(assignedMemberUids);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, true, false, shouldRespond && !u.equals(todo.getCreatedByUser())))
                .collect(Collectors.toSet()));
    }

    private void setConfirmingMembers(Todo todo, Set<String> confirmingMemberUids) {
        Set<TodoAssignment> duplicateAssignments = todo.getAssignments().stream()
                .filter(assignment -> confirmingMemberUids.contains(assignment.getUser().getUid())).collect(Collectors.toSet());
        duplicateAssignments.forEach(todoAssignment -> {
            todoAssignment.setValidator(true);
            todoAssignment.setShouldRespond(true);
        });

        todoAssignmentRepository.saveAll(duplicateAssignments);

        Set<String> newAssignments = new HashSet<>(confirmingMemberUids);
        newAssignments.removeAll(duplicateAssignments.stream().map(a -> a.getUser().getUid()).collect(Collectors.toSet()));
        List<User> users = userService.load(newAssignments);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, false, true, true)).collect(Collectors.toSet()));
        todoRepository.save(todo);
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
    public void cancel(String userUid, String todoUid, boolean sendNotices, String reason) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userService.load(userUid);
        validateUserCanModify(user, todo);
        todo.setCancelled(true);
        log.info("todo is cancelled, notification params: send : {}, reason: {}", sendNotices, reason);
        if (sendNotices) {
            TodoLog todoLog = new TodoLog(TodoLogType.CANCELLED, user, todo, reason);
            final String cancelledMsg = messageService.createTodoCancelledMessage(user, todo);
            Set<Notification> notifications = todo.getAssignments().stream().map(todoAssignment ->
               new TodoCancelledNotification(todoAssignment.getUser(), cancelledMsg, todoLog)).collect(Collectors.toSet());
            storeLogAndNotifications(todoLog, notifications);
        } else {
            createAndStoreTodoLog(user, todo, TodoLogType.CANCELLED, reason);
        }
    }

    @Override
    @Transactional
    public void extend(String userUid, String todoUid, Instant newDueDateTime) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userService.load(userUid);
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
        User user = userService.load(userUid);
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

        User user = userService.load(userUid);
        validateUserCanConfirm(user, todo);

        if(user.getMembership(todo.getAncestorGroup()) == null){
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ALTER_TODO);
        }

        todo.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now());
        createAndStoreTodoLog(user, todo, TodoLogType.RESPONDED, "todo confirmed");
    }

    @Override
    @Transactional(readOnly = true)
    public Todo checkForTodoNeedingResponse(String userUid) {
        Objects.requireNonNull(userUid);

        User user = userService.load(userUid);
        // last in first out (at present - makes most sense if user is responding to something)
        Pageable limit = PageRequest.of(0, 1, new Sort(Sort.Direction.DESC, "createdDateTime"));
        Page<Todo> result = todoRepository.findAll(TodoSpecifications.todosForUserResponse(user), limit);
        return result.getNumberOfElements() == 0 ? null : result.getContent().get(0);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canUserRespond(String userUid, String todoUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userService.load(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        return todoAssignmentRepository.count(TodoSpecifications.userAssignmentCanRespond(user, todo)) > 0;
    }

    @Override
    @Transactional
    public void recordResponse(String userUid, String todoUid, String response, boolean confirmRecorded) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(response);

        Todo todo = todoRepository.findOneByUid(todoUid);

        User user = userService.load(userUid);
        if(user.getMembership(todo.getAncestorGroup()) == null){
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
            default:
                throw new IllegalArgumentException("Error! Todo with non-standard type passed");
        }

        storeLogAndNotifications(todoLog, notifications);
    }

    @Override
    @Transactional
    public void updateTodoCompleted(String userUid, String todoUid, boolean completed) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userService.load(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        validateUserCanModify(user, todo);

        // to make sure reminders are turned off (reminder query should filter out, but just to be sure)
        todo.setNextNotificationTime(null);
        todo.setReminderActive(false);

        todo.setCompleted(completed);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> fetchTodosForUser(String userUid, boolean forceIncludeCreated, boolean limitToNeedingResponse, Instant intervalStart, Instant intervalEnd, Sort sort) {
        Objects.requireNonNull(userUid);
        User user = userService.load(userUid);
        Specification<Todo> specs = limitToNeedingResponse ?
                TodoSpecifications.todosForUserResponse(user) :
                Specification.where(TodoSpecifications.userPartOfParent(user));

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
    public Page<Todo> fetchPageOfTodosForUser(String userUid, boolean createdOnly, Pageable pageRequest) {
        Objects.requireNonNull(userUid);
        User user = userService.load(userUid);
        log.info("page request in here: {}", pageRequest.toString());
        Specification<Todo> specs = Specification.where(TodoSpecifications.todosUserCreated(user));
        if (!createdOnly) {
            specs = specs.and(TodoSpecifications.todosUserAssigned(user));
        }
        return todoRepository.findAll(specs, pageRequest);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Todo> fetchTodosForGroup(String userUid, String groupUid, boolean limitToNeedingResponse, boolean limitToIncomplete,
                                         Instant start, Instant end, Sort sort) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userService.load(userUid);
        Group group = uidIdentifiableRepository.findOneByUid(Group.class, JpaEntityType.GROUP, groupUid);
        // this is causing more trouble than it's worth, so removing it for now
//        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_READ_UPCOMING_EVENTS);

        Specification<Todo> specs;
        if (limitToNeedingResponse) {
            specs = TodoSpecifications.todosForUserResponse(user).and(TodoSpecifications.hasGroupAsParent(group));
        } else {
            specs = Specification.where(TodoSpecifications.hasGroupAsParent(group));
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

        User user = userService.load(userUid);
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
    public List<TaskTimeChangedDTO> fetchUserTodosWithTimeChanged(String userUid) {
        Objects.requireNonNull(userUid);
        User user = userService.load(userUid);
        return todoRepository.fetchTodosWithTimeChangedForUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TodoAssignment> fetchAssignedUserResponses(String userUid, String todoUid, boolean respondedOnly, boolean assignedOnly, boolean witnessOnly) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        User user = userService.load(userUid);
        Todo todo = todoRepository.findOneByUid(todoUid);

        if (!todo.getCreatedByUser().equals(user) || todoAssignmentRepository.count(TodoSpecifications.userAssignment(user, todo)) == 0) {
            throw new AccessDeniedException("Error, only creating or assigned user can see todo details");
        }

        Specification<TodoAssignment> specs = Specification.where((root, query, cb) -> cb.equal(root.get(TodoAssignment_.todo), todo));

        if (respondedOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.hasResponded)));
        }

        if (assignedOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.assignedAction)));
        }

        if (witnessOnly) {
            specs = specs.and((root, query, cb) -> cb.isTrue(root.get(TodoAssignment_.validator)));
        }

        List<Sort.Order> orders = Arrays.asList(Sort.Order.by("hasResponded"), new Sort.Order(Sort.Direction.DESC, "responseTime"));
        return todoAssignmentRepository.findAll(specs, Sort.by(orders));
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

        members.stream()
                .filter(m -> !todo.isCompletionConfirmedByMember(m))
                .peek(member -> {
                    String message = messageService.createTodoReminderMessage(member, todo);
                    Notification notification = new TodoReminderNotification(member, message, todoLog);
                    bundle.addNotification(notification);
                })
                .filter(userService::shouldSendLanguageText)
                .forEach(user -> {
                    log.info("Along with todo reminder, notifying a user about multiple languages ...");
                    UserLog userLog = new UserLog(user.getUid(), UserLogType.NOTIFIED_LANGUAGES, null, UserInterfaceType.SYSTEM);
                    bundle.addLog(userLog);
                    bundle.addNotification(new UserLanguageNotification(user, messageService.createMultiLanguageMessage(), userLog));
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

        Group group = assignment.getTodo().getAncestorGroup();
        Membership membership = assignment.getUser().getMembership(group);

        final String responseTag = assignment.getTodo().getResponseTag();
        if (StringUtils.isEmpty(responseTag)) {
            log.error("Error! Legacy of todo bug, with empty response tag, have to exit");
            return new HashSet<>();
        }

        final String memberTag = responseTag + ":" + response;

        log.info("response tag: {}", responseTag);
        final Optional<String> currentTag = membership.getTagList().stream()
                .filter(Objects::nonNull)
                .filter(s -> s.startsWith(responseTag)).findFirst();

        log.info("adding tag to member: {}, has already: {}", memberTag, currentTag);

        currentTag.ifPresent(membership::removeTag);
        membership.addTag(memberTag);

        if (sendConfirmation) {
            final String message = messageService.createTodoInfoConfirmationMessage(assignment);
            notifications.add(new TodoInfoNotification(assignment.getUser(), message, todoLog));
        }

        return notifications;
    }

    private Set<Notification> processValidation(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : as below, handle different kinds of response better (NLU todo)
        if (assignment.getTodo().getAncestorGroup().isPaidFor() && "yes".equalsIgnoreCase(response)) {
                final String message = messageService.createTodoValidatedMessage(assignment);
                notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(),
                        message, todoLog));
        }
        return notifications;
    }

    private Set<Notification> notifyCreatorOfVolunteer(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : handle non-predictable text entry (use NLU)
        if ("yes".equalsIgnoreCase(response)) {
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

    private void validateTodoInFuture(TodoHelper todoHelper) {
        if (todoHelper.getDueDateTime().isBefore(Instant.now())) {
            throw new TodoDeadlineNotInFutureException();
        }
    }

    private void validateUserCanModify(User user, Todo todo) {
        if (!todo.getCreatedByUser().equals(user) &&
                !permissionBroker.isGroupPermissionAvailable(user, todo.getAncestorGroup(), Permission.GROUP_PERMISSION_ALTER_TODO)) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_ALTER_TODO);
        }
    }

    private void validateUserCanConfirm(User user, Todo todo) {
        if (!todo.getConfirmingUsers().contains(user)) {
            throw new ResponseNotAllowedException();
        }
    }

    private TodoAssignment validateUserCanRespondToTodo(User user, Todo todo) {
        return todoAssignmentRepository.findOne(TodoSpecifications.userAssignment(user, todo))
                .orElseThrow(ResponseNotAllowedException::new);
    }
}
