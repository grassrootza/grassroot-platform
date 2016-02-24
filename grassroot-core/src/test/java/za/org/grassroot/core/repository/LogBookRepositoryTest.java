package za.org.grassroot.core.repository;

import edu.emory.mathcs.backport.java.util.Arrays;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.TestContextConfiguration;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.util.AppIdGenerator;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Date;
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

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "001111141"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(groupUnrelated.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupId(group.getId());
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndNotCompleted()  {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "001111142"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(groupUnrelated.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupIdAndCompletedAndRecorded(group.getId(), false, true);
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndCompleted()  {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "001111143"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupIdAndCompletedAndRecorded(group.getId(), false, true);
        assertEquals(2,list.size());
        lb1.setCompleted(true);
        lb1 = logBookRepository.save(lb1);
        list = logBookRepository.findAllByGroupIdAndCompletedAndRecorded(group.getId(),false, true);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUser()  {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "001111144"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2), user.getId()));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"not assigned", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserIdAndRecorded(user.getId(), true);
        assertEquals(1,list.size());
        lb2.setAssignedToUserId(user.getId());
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserIdAndRecorded(user.getId(), true);
        assertEquals(2,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUserAndCompleted()  {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "001111145"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2),user.getId()));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"not assigned", DateTimeUtil.addHoursFromNow(2),user.getId()));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserIdAndRecordedAndCompleted(user.getId(), true, true);
        assertEquals(0,list.size());
        lb2.setCompleted(true);
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserIdAndRecordedAndCompleted(user.getId(), true, true);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveReplicatedEntries() {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "08601112222"));
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
        LogBook lb1 = logBookRepository.save(new LogBook(group1.getId(), message, Timestamp.valueOf(LocalDateTime.now().plusHours(2))));
        List<LogBook> replicatedEntries = new ArrayList<>();
        for (Group group : subGroups)
            replicatedEntries.add(logBookRepository.save(new LogBook(user.getId(), lb1.getCreatedDateTime(), group.getId(), group1.getId(), message,
                                                         Timestamp.valueOf(LocalDateTime.now().plusHours(2L)))));

        List<LogBook> replicatedEntries2 = new ArrayList<>();
        LogBook lb2 = logBookRepository.save(new LogBook(group1.getId(), message, Timestamp.valueOf(LocalDateTime.now().plusMonths(2L))));
        for (Group group : subGroups)
            replicatedEntries2.add(logBookRepository.save(new LogBook(user.getId(), lb2.getCreatedDateTime(), group.getId(), group1.getId(),
                                                                      message, Timestamp.valueOf(LocalDateTime.now().plusMonths(2L)))));

        List<LogBook> entriesFromDb = logBookRepository.
                findAllByReplicatedGroupIdAndMessageAndCreatedDateTimeOrderByGroupIdAsc(group1.getId(), message, lb1.getCreatedDateTime());

        assertEquals(entriesFromDb.size(), replicatedEntries.size());
        for (int i = 0; i < entriesFromDb.size(); i++)
            assertEquals(entriesFromDb.get(i), replicatedEntries.get(i));

        List<Group> subGroupsFromEntries = new ArrayList<>();
        for (LogBook lb : entriesFromDb)
            subGroupsFromEntries.add(groupRepository.findOne(lb.getGroupId()));

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

    @Test
    public void shouldNotRetrieveUnrecordedEntries() {

        User user = userRepository.save(new User(AppIdGenerator.generateId(), "0601110000"));
        Group group1 = groupRepository.save(new Group("test entry recorded field", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group1.getId(), "user didn't finish this one",
                                                         Timestamp.valueOf(LocalDateTime.now().plusDays(1L)), false));
        LogBook lb2 = logBookRepository.save(new LogBook(group1.getId(), "this one should be recorded",
                                                         Timestamp.valueOf(LocalDateTime.now().plusWeeks(1L)), true));

        List<LogBook> retrievedEntries = logBookRepository.findAllByGroupIdAndRecorded(group1.getId(), true);
        assertFalse(retrievedEntries.contains(lb1));
        assertTrue(retrievedEntries.contains(lb2));
    }

}
