package za.org.grassroot.core.repository;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;


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



    @Test
    public void shouldSaveAndRetrieveLogBookForGroup()  {

        User user = userRepository.save(new User("001111141"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group,"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(user, groupUnrelated, "just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupId(group.getId());
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndNotCompleted()  {

        User user = userRepository.save(new User("001111142"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, "just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(user, groupUnrelated, "just do it too", DateTimeUtil.addHoursFromNow(2)));
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndCompleted()  {

        User user = userRepository.save(new User("001111143"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group,"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lb2 = logBookRepository.save(new LogBook(user, group, "just do it too", DateTimeUtil.addHoursFromNow(2)));
        lb1.setCompleted(true);
        lb1 = logBookRepository.save(lb1);
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUser()  {

        User user = userRepository.save(new User("001111144"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, null, "just do it", DateTimeUtil.addHoursFromNow(2), user, 60));
        LogBook lb2 = logBookRepository.save(new LogBook(user, group, "not assigned", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserId(user.getId());
        assertEquals(1,list.size());
        lb2.setAssignedToUser(user);
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserId(user.getId());
        assertEquals(2,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUserAndCompleted()  {

        User user = userRepository.save(new User("001111145"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(user, group, null, "just do it", DateTimeUtil.addHoursFromNow(2), user, 60));
        LogBook lb2 = logBookRepository.save(new LogBook(user, group, null, "not assigned", DateTimeUtil.addHoursFromNow(2), user, 60));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserIdAndCompleted(user.getId(), true);
        assertEquals(0, list.size());
        lb2.setCompleted(true);
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserIdAndCompleted(user.getId(), true);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveReplicatedEntries() {

        User user = userRepository.save(new User("08601112222"));
        Group group1 = groupRepository.save(new Group("test replicating logbooks", user));
        Group group2 = groupRepository.save(new Group("subgroup1", user, group1));
        Group group3 = groupRepository.save(new Group("subgroup2", user, group1));
        Group group4 = groupRepository.save(new Group("subgroup11", user, group2));
        Group group5 = groupRepository.save(new Group("group2", user));

        List<Group> subGroups = groupRepository.findByParentOrderByIdAsc(group1);
        assertTrue(subGroups.contains(group2));
        assertTrue(subGroups.contains(group3));
        subGroups.add(group4);
        assertThat(subGroups.size(), is(3));

        String message = "check replicating logbooks";
        LogBook lb1 = logBookRepository.save(new LogBook(user, group1, message, Timestamp.valueOf(LocalDateTime.now().plusHours(2))));
        List<LogBook> replicatedEntries = new ArrayList<>();
        for (Group group : subGroups)
            replicatedEntries.add(logBookRepository.save(new LogBook(user, group, group1, message,
                                                         Timestamp.valueOf(LocalDateTime.now().plusHours(2L)), null, 60)));

        List<LogBook> replicatedEntries2 = new ArrayList<>();
        LogBook lb2 = logBookRepository.save(new LogBook(user, group1, message, Timestamp.valueOf(LocalDateTime.now().plusMonths(2L))));
        for (Group group : subGroups)
            replicatedEntries2.add(logBookRepository.save(new LogBook(user, group, group1, message,
                    Timestamp.valueOf(LocalDateTime.now().plusMonths(2L)), null, 60)));

        List<LogBook> entriesFromDb = logBookRepository.
                findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(group1.getId(), message, lb1.getCreatedDateTime());

        assertEquals(entriesFromDb.size(), replicatedEntries.size());
        for (int i = 0; i < entriesFromDb.size(); i++)
            assertEquals(entriesFromDb.get(i), replicatedEntries.get(i));

        List<Group> subGroupsFromEntries = new ArrayList<>();
        for (LogBook lb : entriesFromDb)
            subGroupsFromEntries.add(groupRepository.findOne(lb.getGroup().getId()));

        assertFalse(subGroupsFromEntries.contains(group1));
        assertTrue(subGroupsFromEntries.contains(group2));
        assertTrue(subGroupsFromEntries.contains(group3));
        assertTrue(subGroupsFromEntries.contains(group4));
        assertFalse(subGroupsFromEntries.contains(group5));

        List<LogBook> entriesFromDb2 = logBookRepository.
                findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(group1.getId(), message, lb2.getCreatedDateTime());
        assertEquals(entriesFromDb2, replicatedEntries2);

        int numberReplicatedEntries1 = logBookRepository.countReplicatedEntries(group1.getId(), message, lb1.getCreatedDateTime());
        assertEquals(numberReplicatedEntries1, entriesFromDb.size());

        int numberReplicatedEntries2 = logBookRepository.countReplicatedEntries(group1.getId(), message, lb2.getCreatedDateTime());
        assertEquals(numberReplicatedEntries2, entriesFromDb2.size());
    }
}
