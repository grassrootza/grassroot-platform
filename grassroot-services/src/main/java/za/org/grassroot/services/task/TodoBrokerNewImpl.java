package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
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
    private final UserRepository userRepository;
    private final UidIdentifiableRepository uidIdentifiableRepository;

    private final PermissionBroker permissionBroker;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;

    private final ApplicationEventPublisher eventPublisher;

    @Autowired
    public TodoBrokerNewImpl(TodoRepository todoRepository, UserRepository userRepository, UidIdentifiableRepository uidIdentifiableRepository, PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker, ApplicationEventPublisher eventPublisher) {
        this.todoRepository = todoRepository;
        this.userRepository = userRepository;
        this.uidIdentifiableRepository = uidIdentifiableRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
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

        Set<Notification> notifications = wireUpTodoForType(todo, todoHelper);
        createAndStoreTodoLog(user, todo, TodoLogType.CREATED, null, notifications);

        todo = todoRepository.save(todo);
        return todo.getUid();
    }

    private Set<Notification> wireUpTodoForType(Todo todo, TodoHelper todoHelper) {
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

        assignedNonConfirmingUsers.forEach(u -> notifications.add(generateTodoAssignedNotification(todo.getType(), u)));
        confirmingUsers.forEach(u -> notifications.add(generateNotificationForConfirmingUsers(todo.getType(), u)));

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

    private Notification generateTodoAssignedNotification(TodoType todoType, User target) {
        return null;
    }

    private Notification generateNotificationForConfirmingUsers(TodoType todoType, User target) {
        return null;
    }

    @Override
    @Transactional
    public void cancel(String userUid, String todoUid, String reason) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setCancelled(true);
        // todo : send out notifications
        createAndStoreTodoLog(user, todo, TodoLogType.CANCELLED, reason, null);
    }

    @Override
    @Transactional
    public void extend(String userUid, String todoUid, Instant newDueDateTime) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setActionByDate(newDueDateTime);
        // todo : consider notifications
        createAndStoreTodoLog(user, todo, TodoLogType.EXTENDED, newDueDateTime.toString(), null);
    }

    @Override
    @Transactional
    public void updateDescription(String userUid, String todoUid, String newDescription) {
        Todo todo = todoRepository.findOneByUid(todoUid);
        User user = userRepository.findOneByUid(userUid);
        validateUserCanModify(user, todo);
        todo.setDescription(newDescription);
        createAndStoreTodoLog(user, todo, TodoLogType.CHANGED, "Changed description: " + newDescription, null);
    }

    @Override
    @Transactional
    public void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getType().equals(TodoType.CONFIRMATION_REQUIRED)) {
            throw new TodoTypeMismatchException();
        }

        User user = userRepository.findOneByUid(userUid);
        validateUserCanConfirm(user, todo);

        todo.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now(), null);

        createAndStoreTodoLog(user, todo, TodoLogType.RESPONDED, "todo confirmed", null);
    }

    @Override
    @Transactional(readOnly = true)
    public void hasInformationRequested(String userUid, String responseString) {
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);

        todoRepository.findAll();
    }

    @Override
    @Transactional
    public void recordResponse(String userUid, String todoUid, String response) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoUid);
        Objects.requireNonNull(response);

        Todo todo = todoRepository.findOneByUid(todoUid);
        if (!todo.getType().equals(TodoType.INFORMATION_REQUIRED)) {
            throw new TodoTypeMismatchException();
        }

        // todo : decide if this is tagged in parent groups or just parent
        // todo : enforce assignment only within group members when create

        User user = userRepository.findOneByUid(userUid);
        validateUserCanRespondToTodo(user, todo);

        Group group = todo.getAncestorGroup();
        Membership membership = group.getMembership(user);

        // todo : some validation in here
        final String memberTag = todo.getResponseTag() + ":" + response;
        membership.addTag(memberTag);

        createAndStoreTodoLog(user, todo, TodoLogType.RESPONDED, memberTag, null);
    }

    private void createAndStoreTodoLog(User user, Todo todo, TodoLogType todoLogType, String description,
                                       Set<Notification> notifications) {
        TodoLog todoLog = new TodoLog(todoLogType, user, todo, description);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(todoLog);
        if (notifications != null) {
            bundle.addNotifications(notifications);
        }
        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.storeBundle(bundle);
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

    private void validateUserCanRespondToTodo(User user, Todo todo) {
        if (todo.getType().equals(TodoType.INFORMATION_REQUIRED)) {
            if (!todo.getMembers().contains(user)) {
                throw new ResponseNotAllowedException();
            }
        }
    }
}
