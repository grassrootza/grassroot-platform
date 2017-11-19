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
import za.org.grassroot.core.domain.task.TodoLog;
import za.org.grassroot.core.dto.task.TaskTimeChangedDTO;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.enums.TodoLogType;
import za.org.grassroot.core.specifications.TodoSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.junit.Assert.*;


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
    public void shouldSaveAndRetrieveLogBookForGroup()  {

        User user = userRepository.save(new User("001111141"));
        Group group = groupRepository.save(new Group("test action", user));
        Group groupUnrelated = groupRepository.save(new Group("not related action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, "just do it", addHoursFromNow(2)));
        Todo lbUnrelated = todoRepository.save(new Todo(user, groupUnrelated, "just do it too", addHoursFromNow(2)));
        List<Todo> list = todoRepository.findAll(TodoSpecifications.hasGroupAsParent(group));
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndNotCompleted()  {

        User user = userRepository.save(new User("001111142"));
        Group group = groupRepository.save(new Group("test action", user));
        Group groupUnrelated = groupRepository.save(new Group("not related action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, "just do it", addHoursFromNow(2)));
        Todo lbUnrelated = todoRepository.save(new Todo(user, groupUnrelated, "just do it too", addHoursFromNow(2)));
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndCompleted()  {

        User user = userRepository.save(new User("001111143"));
        Group group = groupRepository.save(new Group("test action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group,"just do it", addHoursFromNow(2)));
        Todo lb2 = todoRepository.save(new Todo(user, group, "just do it too", addHoursFromNow(2)));
        lb1 = todoRepository.save(lb1);
    }

    @Test
    public void shouldSaveAndRetrieveTodoAssignedToUserAndCompleted()  {
        User user = userRepository.save(new User("001111145"));
        Group group = new Group("test action", user);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        groupRepository.save(group);

        Todo lb1 = new Todo(user, group, "assigned 1", addHoursFromNow(2), 60, true);
        Todo lb2 = new Todo(user, group, "not assigned", addHoursFromNow(2), 60, true);
        lb1.assignMembers(Collections.singleton(user.getUid()));

        todoRepository.save(lb1);
        todoRepository.save(lb2);

        // todo : move these into services, given transition to using specifications
        Sort sort = new Sort(Sort.Direction.DESC, "actionByDate");
        List<Todo> list = todoRepository.findByAssignedMembersAndActionByDateBetweenAndCompletedTrue(user, Instant.now(),
                DateTimeUtil.getVeryLongAwayInstant(), sort);
        assertEquals(0, list.size());
        lb1.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now());
        todoRepository.save(lb1);
        list = todoRepository.findByAssignedMembersAndActionByDateBetweenAndCompletedTrue(user, Instant.now(),
                DateTimeUtil.getVeryLongAwayInstant(), sort);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldRetrieveTodosWithTimeChanged() {
        User user = userRepository.save(new User("0601110000"));
        Group group = new Group("test", user);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER);
        groupRepository.save(group);

        Todo todo1 = todoRepository.save(new Todo(user, group, "firstOne", addHoursFromNow(2), 60, true));
        Todo todo2 = todoRepository.save(new Todo(user, group, "secondOne", addHoursFromNow(2), 60, true));

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
