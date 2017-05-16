package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Todo;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.TodoCompletionConfirmType;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
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

    private Instant addHoursFromNow(int hours) { return Instant.now().plus(hours, ChronoUnit.HOURS); }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroup()  {

        User user = userRepository.save(new User("001111141"));
        Group group = groupRepository.save(new Group("test action", user));
        Group groupUnrelated = groupRepository.save(new Group("not related action", user));
        Todo lb1 = todoRepository.save(new Todo(user, group, "just do it", addHoursFromNow(2)));
        Todo lbUnrelated = todoRepository.save(new Todo(user, groupUnrelated, "just do it too", addHoursFromNow(2)));
        List<Todo> list = todoRepository.findByParentGroupAndCancelledFalse(group);
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

/*
    public void shouldSaveAndRetrieveLogBookAssignedToUser()  {

        User user = userRepository.save(new User("001111144"));
        Group group = groupRepository.save(new Group("test action", user));
        To-do lb1 = todoRepository.save(new To-do(user, group, "just do it", addHoursFromNow(2), 60, null, 3));
        To-do lb2 = todoRepository.save(new To-do(user, group, "not assigned", addHoursFromNow(2)));
        List<To-do> list = todoRepository.findByAssignedMembersAndActionByDateGreaterThan(user, Instant.now());
        assertEquals(1,list.size());
//        lb2.setAssignedToUser(user);
        lb2 = todoRepository.save(lb2);
        list = todoRepository.findByAssignedMembersAndActionByDateGreaterThan(user, Instant.now());
        assertEquals(2,list.size());
    }
*/

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUserAndCompleted()  {
        User user = userRepository.save(new User("001111145"));
        Group group = new Group("test action", user);
        group.addMember(user);
        groupRepository.save(group);

        Todo lb1 = new Todo(user, group, "assigned 1", addHoursFromNow(2), 60, null, 3, true);
        Todo lb2 = new Todo(user, group, "not assigned", addHoursFromNow(2), 60, null, 3, true);
        lb1.assignMembers(Collections.singleton(user.getUid()));

        todoRepository.save(lb1);
        todoRepository.save(lb2);

        // todo : move these into services, given transition to using specifications
        Sort sort = new Sort(Sort.Direction.DESC, "actionByDate");
        List<Todo> list = todoRepository.findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(user, Instant.now(),
                DateTimeUtil.getVeryLongAwayInstant(), 50, sort);
        assertEquals(0, list.size());
        lb1.addCompletionConfirmation(user, TodoCompletionConfirmType.COMPLETED, Instant.now(), 20);
        todoRepository.save(lb1);
        list = todoRepository.findByAssignedMembersAndActionByDateBetweenAndCompletionPercentageGreaterThanEqual(user, Instant.now(),
                DateTimeUtil.getVeryLongAwayInstant(), 50, sort);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveReplicatedEntries() {

        User user = userRepository.save(new User("08601112222"));
        Group groupParent = groupRepository.save(new Group("test replicating actions", user));
        Group group2 = groupRepository.save(new Group("subgroup1", user, groupParent));
        Group group3 = groupRepository.save(new Group("subgroup2", user, groupParent));
        Group group4 = groupRepository.save(new Group("subgroup11", user, group2));
        Group group5 = groupRepository.save(new Group("group2", user));

        List<Group> subGroups = groupRepository.findAll(Specifications.where(
                GroupSpecifications.hasParent(groupParent)).and(GroupSpecifications.isActive()));;
        assertTrue(subGroups.contains(group2));
        assertTrue(subGroups.contains(group3));
        subGroups.add(group4);
        assertThat(subGroups.size(), is(3));

        String message = "check replicating actions";
        Instant dueDate1 = LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.UTC);
        Instant dueDate2 = LocalDateTime.now().plusMonths(2).toInstant(ZoneOffset.UTC);

        Todo lbParent = todoRepository.save(new Todo(user, groupParent, message, dueDate1));

        List<Todo> replicatedEntries = new ArrayList<>();
        for (Group group : subGroups) {
            Todo lbChild = new Todo(user, group, message, dueDate1, 60, lbParent, 3, true);
            todoRepository.save(lbChild);
            replicatedEntries.add(lbChild);
        }

        // note : once fix / change replicated to-do models, fix this too

        List<Todo> replicatedEntries2 = new ArrayList<>();

        Todo lbParent2 = todoRepository.save(new Todo(user, groupParent, message, dueDate2));
        for (Group group : subGroups)
            replicatedEntries2.add(todoRepository.save(new Todo(user, group, message, dueDate2, 60, lbParent2, 3, true)));

        List<Todo> entriesFromDb = todoRepository.findBySourceTodo(lbParent2);

        assertEquals(entriesFromDb.size(), replicatedEntries.size());
        for (int i = 0; i < entriesFromDb.size(); i++)
            assertEquals(entriesFromDb.get(i), replicatedEntries2.get(i));

        List<Group> subGroupsFromEntries = new ArrayList<>();
        for (Todo lb : entriesFromDb)
            subGroupsFromEntries.add(groupRepository.findOne(lb.getParent().getId()));

        assertFalse(subGroupsFromEntries.contains(groupParent));
        assertTrue(subGroupsFromEntries.contains(group2));
        assertTrue(subGroupsFromEntries.contains(group3));
        assertTrue(subGroupsFromEntries.contains(group4));
        assertFalse(subGroupsFromEntries.contains(group5));

        List<Todo> entriesFromDb2 = todoRepository.findBySourceTodo(lbParent2);
        assertEquals(entriesFromDb2, replicatedEntries2);

        int numberReplicatedEntries1 = todoRepository.countBySourceTodo(lbParent);
        assertEquals(numberReplicatedEntries1, entriesFromDb.size());

        int numberReplicatedEntries2 = todoRepository.countBySourceTodo(lbParent2);
        assertEquals(numberReplicatedEntries2, entriesFromDb2.size());
    }
}
