package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoContainer;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.repository.TodoRepository;
import za.org.grassroot.core.repository.UidIdentifiableRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.MemberLacksPermissionException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

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

        if (todoHelper.hasAssignedMembers()) {

        } else {
            notifications = todo.getParent().getMembers().forEach();
        }

        if (todoHelper.hasConfirmationMembers()) {

        }
        return notifications;
    }

    private Notification generateTodoCreatedNotification(TodoType todoType, User target) {
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
    public void confirmCompletion(String userUid, String todoUid, String notes, Set<String> taskImageUids) {

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
}
