package za.org.grassroot.services.task;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.TodoRequest;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.TodoRequestRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;

import java.time.LocalDateTime;
import java.util.Objects;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@Service @Slf4j
public class TodoRequestBrokerImpl implements TodoRequestBroker {

	private final UserRepository userRepository;
	private final GroupRepository groupRepository;
	private final TodoBroker todoBroker;
	private final PermissionBroker permissionBroker;
	private final AccountGroupBroker accountGroupBroker;
	private final TodoRequestRepository todoRequestRepository;

    @Autowired
    public TodoRequestBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, TodoBroker todoBroker, PermissionBroker permissionBroker, AccountGroupBroker accountGroupBroker, TodoRequestRepository todoRequestRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.todoBroker = todoBroker;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.todoRequestRepository = todoRequestRepository;
    }

    @Override
	public TodoRequest load(String requestUid) {
		return todoRequestRepository.findOneByUid(requestUid);
	}

    @Override
    @Transactional
    public TodoRequest create(String userUid, TodoType todoType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(todoType);

        User user = userRepository.findOneByUid(userUid);

		TodoRequest request = new TodoRequest(user, todoType);
		return todoRequestRepository.save(request);
    }

    @Override
    @Transactional
    public void updateGroup(String userUid, String requestUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(requestUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        TodoRequest request= todoRequestRepository.findOneByUid(requestUid);
        validateUserCanModify(user, request);

        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CREATE_LOGBOOK_ENTRY);
        if (accountGroupBroker.numberTodosLeftForGroup(group.getUid()) < 1)
            throw new AccountLimitExceededException();

        request.setParent(group);
    }

    private void validateUserCanModify(User user, TodoRequest request) {
        if (!request.getCreatedByUser().equals(user))
            throw new AccessDeniedException("You are not the creator of this todo");
    }

    @Override
    @Transactional
	public void updateMessage(String userUid, String requestUid, String message) {
		Objects.requireNonNull(userUid);
		Objects.requireNonNull(requestUid);

        User user = userRepository.findOneByUid(userUid);
        TodoRequest todoRequest = todoRequestRepository.findOneByUid(requestUid);
        validateUserCanModify(user, todoRequest);

        log.info("updating request subject to {}, request looks like {}", message, todoRequest);

        todoRequest.setMessage(message);
	}

    @Override
    @Transactional
    public void updateDueDate(String userUid, String requestUid, LocalDateTime dueDate) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(requestUid);

        User user = userRepository.findOneByUid(userUid);
        TodoRequest todoRequest = todoRequestRepository.findOneByUid(requestUid);
        validateUserCanModify(user, todoRequest);

        log.info("updating due date to {}, request looks like {}", dueDate, todoRequest);

        todoRequest.setActionByDate(dueDate != null ? convertToSystemTime(dueDate, getSAST()) : null);
    }

    @Override
    @Transactional
    public void updateResponseTag(String userUid, String requestUid, String responseTag) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(requestUid);
        Objects.requireNonNull(responseTag);

        User user = userRepository.findOneByUid(userUid);
        TodoRequest request = todoRequestRepository.findOneByUid(requestUid);
        validateUserCanModify(user, request);

        log.info("updating response tag to {}, request looks like {}", responseTag, request);

        request.setResponseTag(responseTag);
    }

    @Override
	@Transactional
	public void finish(String todoUid) {
		Objects.requireNonNull(todoUid);

		TodoRequest request = todoRequestRepository.findOneByUid(todoUid);

		TodoHelper helper = TodoHelper.builder()
                .todoType(request.getType())
                .userUid(request.getCreatedByUser().getUid())
                .parentType(request.getParent().getJpaEntityType())
                .parentUid(request.getParent().getUid())
                .subject(request.getMessage())
                .dueDateTime(request.getActionByDate()).build();

		todoBroker.create(helper);
		todoRequestRepository.delete(request);
	}
}
