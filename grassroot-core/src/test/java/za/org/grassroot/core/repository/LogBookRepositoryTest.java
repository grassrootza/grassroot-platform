package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.*;


@RunWith(SpringJUnit4ClassRunner.class)
@SpringApplicationConfiguration(classes = TestContextConfiguration.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class LogBookRepositoryTest {

    @Autowired
    LogBookRepository logBookRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    UserRepository userRepository;

    private Instant addHoursFromNow(int hours) { return Instant.now().plus(hours, ChronoUnit.HOURS); }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroup()  {

        User user = userRepository.save(new User("001111141"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, "just do it", addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(user, groupUnrelated, "just do it too", addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findByGroup(group);
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndNotCompleted()  {

        User user = userRepository.save(new User("001111142"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, "just do it", addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(user, groupUnrelated, "just do it too", addHoursFromNow(2)));
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndCompleted()  {

        User user = userRepository.save(new User("001111143"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group,"just do it", addHoursFromNow(2)));
        LogBook lb2 = logBookRepository.save(new LogBook(user, group, "just do it too", addHoursFromNow(2)));
        lb1.setCompleted(true);
        lb1 = logBookRepository.save(lb1);
    }

    public void shouldSaveAndRetrieveLogBookAssignedToUser()  {

        User user = userRepository.save(new User("001111144"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, "just do it", addHoursFromNow(2), 60, null, 3));
        LogBook lb2 = logBookRepository.save(new LogBook(user, group, "not assigned", addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByAssignedMembersId(user.getId());
        assertEquals(1,list.size());
//        lb2.setAssignedToUser(user);
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedMembersId(user.getId());
        assertEquals(2,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUserAndCompleted()  {

        User user = userRepository.save(new User("001111145"));
        Group group = new Group("test logbook", user);
        group.addMember(user);
        groupRepository.save(group);

        LogBook lb1 = new LogBook(user, group, "assigned 1", addHoursFromNow(2), 60, null, 3);
        LogBook lb2 = new LogBook(user, group, "not assigned", addHoursFromNow(2), 60, null, 3);
        lb1.assignMembers(Collections.singleton(user.getUid()));

        logBookRepository.save(lb1);
        logBookRepository.save(lb2);

        List<LogBook> list = logBookRepository.findAllByAssignedMembersIdAndCompleted(user.getId(), true);
        assertEquals(0, list.size());
        lb1.setCompleted(true);
        logBookRepository.save(lb1);
        list = logBookRepository.findAllByAssignedMembersIdAndCompleted(user.getId(), true);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveReplicatedEntries() {

        User user = userRepository.save(new User("08601112222"));
        Group groupParent = groupRepository.save(new Group("test replicating logbooks", user));
        Group group2 = groupRepository.save(new Group("subgroup1", user, groupParent));
        Group group3 = groupRepository.save(new Group("subgroup2", user, groupParent));
        Group group4 = groupRepository.save(new Group("subgroup11", user, group2));
        Group group5 = groupRepository.save(new Group("group2", user));

        List<Group> subGroups = groupRepository.findByParentOrderByIdAsc(groupParent);
        assertTrue(subGroups.contains(group2));
        assertTrue(subGroups.contains(group3));
        subGroups.add(group4);
        assertThat(subGroups.size(), is(3));

        String message = "check replicating logbooks";
        Instant dueDate1 = LocalDateTime.now().plusHours(2).toInstant(ZoneOffset.UTC);
        Instant dueDate2 = LocalDateTime.now().plusMonths(2).toInstant(ZoneOffset.UTC);

        LogBook lbParent = logBookRepository.save(new LogBook(user, groupParent, message, dueDate1));

        List<LogBook> replicatedEntries = new ArrayList<>();
        for (Group group : subGroups) {
            LogBook lbChild = new LogBook(user, group, message, dueDate1, 60, groupParent, 3);
            logBookRepository.save(lbChild);
            replicatedEntries.add(lbChild);
        }

        // todo: change logbook design so replication references logbook entry, not group, then don't need the
        // todo: (contd) query checking date & time (which is not going to work) or message (which risks false returns in real life)

        List<LogBook> replicatedEntries2 = new ArrayList<>();
        LogBook lbParent2 = logBookRepository.save(new LogBook(user, groupParent, message, dueDate2));
        for (Group group : subGroups)
            replicatedEntries2.add(logBookRepository.save(new LogBook(user, group, message, dueDate2, 60, groupParent, 3)));

        List<LogBook> entriesFromDb = logBookRepository.
                findAllByReplicatedGroupIdAndMessageAndActionByDateOrderByGroupIdAsc(groupParent.getId(), message, dueDate1);

        assertEquals(entriesFromDb.size(), replicatedEntries.size());
        for (int i = 0; i < entriesFromDb.size(); i++)
            assertEquals(entriesFromDb.get(i), replicatedEntries.get(i));

        List<Group> subGroupsFromEntries = new ArrayList<>();
        for (LogBook lb : entriesFromDb)
            subGroupsFromEntries.add(groupRepository.findOne(((Group)lb.getParent()).getId()));

        assertFalse(subGroupsFromEntries.contains(groupParent));
        assertTrue(subGroupsFromEntries.contains(group2));
        assertTrue(subGroupsFromEntries.contains(group3));
        assertTrue(subGroupsFromEntries.contains(group4));
        assertFalse(subGroupsFromEntries.contains(group5));

        List<LogBook> entriesFromDb2 = logBookRepository.
                findAllByReplicatedGroupIdAndMessageAndActionByDateOrderByGroupIdAsc(groupParent.getId(), message, lbParent2.getActionByDate());
        assertEquals(entriesFromDb2, replicatedEntries2);

        int numberReplicatedEntries1 = logBookRepository.countReplicatedEntries(groupParent.getId(), message, lbParent.getActionByDate());
        assertEquals(numberReplicatedEntries1, entriesFromDb.size());

        int numberReplicatedEntries2 = logBookRepository.countReplicatedEntries(groupParent.getId(), message, lbParent2.getActionByDate());
        assertEquals(numberReplicatedEntries2, entriesFromDb2.size());
    }
}
