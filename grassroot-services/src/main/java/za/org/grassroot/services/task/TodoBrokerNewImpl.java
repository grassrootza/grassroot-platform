package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.TodoInfoNotification;
import za.org.grassroot.core.domain.task.*;
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
import za.org.grassroot.services.exception.TodoTypeMismatchException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class TodoBrokerNewImpl implements TodoBrokerNew {

    private final TodoRepository todoRepository;
    private final TodoAssignmentRepository todoAssignmentRepository;
    private final UserRepository userRepository;
    private final UidIdentifiableRepository uidIdentifiableRepository;

    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private final MessageAssemblingService messageService;
    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TodoBrokerNewImpl(TodoRepository todoRepository, TodoAssignmentRepository todoAssignmentRepository, UserRepository userRepository, UidIdentifiableRepository uidIdentifiableRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, MessageAssemblingService messageService, ApplicationEventPublisher eventPublisher) {
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
    public Todo load(String todoUid) {
        Objects.requireNonNull(todoUid);
        return todoRepository.findOneByUid(todoUid);
    }

    @Override
    @Transactional
    public String create(TodoHelper todoHelper) {
        todoHelper.validateMinimumFields();

        User user = userRepository.findOneByUid(todoHelper.getUserUid());
        TodoContainer parent = uidIdentifiableRepository.findOneByUid(TodoContainer.class,
                todoHelper.getParentType(), todoHelper.getParentUid());

        validateUserCanCreate(user, parent.getThisOrAncestorGroup());

        Todo todo = new Todo(user, parent, todoHelper.getTodoType(), todoHelper.getDescription(), todoHelper.getDueDateTime());
        todo = todoRepository.save(todo);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        TodoLog todoLog = new TodoLog(TodoLogType.CREATED, user, todo, null);
        bundle.addLog(todoLog);
        bundle.addNotifications(wireUpTodoForType(todo, todoHelper, todoLog));
        logsAndNotificationsBroker.storeBundle(bundle);

        return todo.getUid();
    }

    private Set<Notification> wireUpTodoForType(Todo todo, TodoHelper todoHelper, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        if (todoHelper.isInformationTodo()) {
            todo.setResponseTag(todoHelper.getResponseTag());
        }

        if (todoHelper.getAssignedMemberUids() == null || todoHelper.getAssignedMemberUids().isEmpty()) {
            setAllParentMembersAssigned(todo);
        } else {
            setAssignedMembers(todo, todoHelper.getAssignedMemberUids());
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

    // todo : as below, proper validation on types, combinations, etc (e.g., responses only within group)
    // todo : handle properly where users are both assigned and confirming
    private void setAllParentMembersAssigned(Todo todo) {
        todo.setAssignments(todo.getParent().getMembers().stream()
                .map(u -> new TodoAssignment(todo, u, true, false)).collect(Collectors.toSet()));
    }

    private void setAssignedMembers(Todo todo, Set<String> assignedMemberUids) {
        List<User> users = userRepository.findByUidIn(assignedMemberUids);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, true, false)).collect(Collectors.toSet()));
    }

    private void setConfirmingMembers(Todo todo, Set<String> confirmingMemberUids) {
        List<User> users = userRepository.findByUidIn(confirmingMemberUids);
        todo.addAssignments(users.stream().map(u -> new TodoAssignment(todo, u, false, true)).collect(Collectors.toSet()));
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
        validateUserCanModify(user, todo);
        todo.setActionByDate(newDueDateTime);
        // todo : consider notifications
        createAndStoreTodoLog(user, todo, TodoLogType.EXTENDED, newDueDateTime.toString());
    }

    @Override
    @Transactional
    public void updateDescription(String userUid, String todoUid, String newDescription) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setDescription(newDescription);
        createAndStoreTodoLog(user, todo, TodoLogType.CHANGED, "Changed description: " + newDescription);
    }

    @Override
    @Transactional
    public void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getType().equals(TodoType.VALIDATION_REQUIRED)) {
            throw new TodoTypeMismatchException();
        }

        User user = userRepository.findOneByUid(userUid);
        validateUserCanConfirm(user, todo);

        todo.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now());
        createAndStoreTodoLog(user, todo, TodoLogType.RESPONDED, "todo confirmed");
    }

    @Override
    @Transactional(readOnly = true)
    public Todo checkForTodoNeedingResponse(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        // last in first out (at present - makes most sense if user is responding to something)
        Pageable limit = new PageRequest(0, 1, new Sort(Sort.Direction.DESC, "createdDateTime"));
        Page<Todo> result = todoRepository.findAll(TodoSpecifications.todosForUserResponse(user), limit);
        return result.getNumberOfElements() == 0 ? null : result.getContent().get(0);
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

    private Set<Notification> recordInformationResponse(TodoAssignment assignment, String response, TodoLog todoLog, boolean sendConfirmation) {
        Set<Notification> notifications = new HashSet<>();

        // todo: handle possible duplication
        Group group = assignment.getTodo().getAncestorGroup();
        Membership membership = group.getMembership(assignment.getUser());

        final String memberTag = assignment.getTodo().getResponseTag() + ":" + response;
        log.info("adding tag to member: {}", memberTag);
        membership.addTag(memberTag);

        if (sendConfirmation) {
            notifications.add(new TodoInfoNotification(assignment.getUser(), "we recorded it", todoLog));
        }
        return notifications;
    }

    private Set<Notification> processValidation(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : restrict notifications to only if on paid account (else just do at deadline)
        // todo : as below, handle different kinds of response better
        if ("yes".equalsIgnoreCase(response)) {
            notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(),
                    "someone validated", todoLog));
        }
        return notifications;
    }

    private Set<Notification> notifyCreatorOfVolunteer(TodoAssignment assignment, String response, TodoLog todoLog) {
        Set<Notification> notifications = new HashSet<>();
        // todo : consider notifying if no volunteer (if on paid account ...)
        // todo : handle non-predictable text entry (use NLU)
        if ("yes".equalsIgnoreCase(response)) {
            notifications.add(new TodoInfoNotification(assignment.getTodo().getCreatedByUser(),
                    "someone volunteered", todoLog));
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
        if (TodoType.VALIDATION_REQUIRED.equals(todo.getType()) && !todoAssignment.isCanWitness()) {
            throw new ResponseNotAllowedException();
        }
        return todoAssignment;
    }
}
