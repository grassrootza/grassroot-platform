package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.TodoAssignment;
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.domain.task.TodoType;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.specifications.TodoSpecifications;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.Assert.*;

// major todo: fix / adapt these for new todo design
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class TodoRepositoryTest {

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TodoLogRepository todoLogRepository;

    private Instant addHoursFromNow(int hours) { return Instant.now().plus(hours, ChronoUnit.HOURS); }

    @Test
    public void shouldSaveAndRetrieveTodoForGroup()  {

        User user = userRepository.save(new User("001111141", null, null));
        Group group = groupRepository.save(new Group("test action", user));
        Group groupUnrelated = groupRepository.save(new Group("not related action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "just do it", addHoursFromNow(2)));
        Todo lbUnrelated = todoRepository.save(new Todo(user, groupUnrelated, TodoType.ACTION_REQUIRED, "just do it too", addHoursFromNow(2)));
        List<Todo> list = todoRepository.findAll(TodoSpecifications.hasGroupAsParent(group));
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveTodoForGroupAndNotCompleted()  {
        // todo: finalize these and subsequent
        User user = userRepository.save(new User("001111142", null, null));
        Group group = groupRepository.save(new Group("test action", user));
        Group groupUnrelated = groupRepository.save(new Group("not related action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "just do it", addHoursFromNow(2)));
        Todo lbUnrelated = todoRepository.save(new Todo(user, groupUnrelated, TodoType.ACTION_REQUIRED, "just do it too", addHoursFromNow(2)));
    }

    @Test
    public void shouldSaveAndRetrieveTodoForGroupAndCompleted()  {

        User user = userRepository.save(new User("001111143", null, null));
        Group group = groupRepository.save(new Group("test action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED,"just do it", addHoursFromNow(2)));
        Todo lb2 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "just do it too", addHoursFromNow(2)));
        lb1 = todoRepository.save(lb1);
    }

    @Test
    public void shouldSaveAndRetrieveTodoAssignedToUserAndCompleted()  {
        User user = userRepository.save(new User("001111145", null, null));
        Group group = new Group("test action", user);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);

        Todo lb1 = new Todo(user, group, TodoType.ACTION_REQUIRED, "assigned 1", addHoursFromNow(2));
        Todo lb2 = new Todo(user, group, TodoType.ACTION_REQUIRED, "not assigned", addHoursFromNow(2));
        lb1.addAssignments(Collections.singleton(new TodoAssignment(lb1, user, false, true, true)));

        todoRepository.save(lb1);
        todoRepository.save(lb2);

        Sort sort = new Sort(Sort.Direction.DESC, "actionByDate");
        List<Todo> list = todoRepository.findAll(TodoSpecifications.todosForUserResponse(user));
        assertEquals(0, list.size());
        lb1.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now());
        todoRepository.save(lb1);
        list = todoRepository.findAll(TodoSpecifications.todosForUserResponse(user));
        assertEquals(0,list.size());
    }

    @Test
    public void shouldRetrieveTodosWithTimeChanged() {
        User user = userRepository.save(new User("0601110000", null, null));
        Group group = new Group("test", user);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groupRepository.save(group);

        Todo todo1 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "firstOne", addHoursFromNow(2)));
        Todo todo2 = todoRepository.save(new Todo(user, group, TodoType.ACTION_REQUIRED, "secondOne", addHoursFromNow(2)));

        TodoLog createdLog = todoLogRepository.save(new TodoLog(TodoLogType.CREATED, user, todo1, "created"));
        TodoLog createdLog1 = todoLogRepository.save(new TodoLog(TodoLogType.CREATED, user, todo2, "created"));

        Set<String> todoUids = new HashSet<>();
        todoUids.add(todo1.getUid());
        todoUids.add(todo2.getUid());

        assertEquals(2, todoRepository.count());

        List<TaskTimeChangedDTO> todosWithTimeChanged = todoRepository.fetchTodosWithTimeChanged(todoUids);
        assertNotNull(todosWithTimeChanged);
        assertEquals(2, todosWithTimeChanged.size());
        List<Instant> creationTimes = Arrays.asList(createdLog.getCreatedDateTime(), createdLog1.getCreatedDateTime());
        assertTrue(creationTimes.contains(todosWithTimeChanged.get(0).getLastTaskChange()));
    }
}
