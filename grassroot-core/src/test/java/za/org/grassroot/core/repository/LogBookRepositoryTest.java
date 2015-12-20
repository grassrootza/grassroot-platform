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
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.util.DateTimeUtil;

import javax.transaction.Transactional;
import java.util.Date;
import java.util.List;

import static org.junit.Assert.assertEquals;


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
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(groupUnrelated.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupId(group.getId());
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndNotCompleted()  {

        User user = userRepository.save(new User("001111142"));
        Group group = groupRepository.save(new Group("test logbook", user));
        Group groupUnrelated = groupRepository.save(new Group("not related logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lbUnrelated = logBookRepository.save(new LogBook(groupUnrelated.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupIdAndCompleted(group.getId(),false);
        assertEquals(1,list.size());
        assertEquals(lb1.getId(),list.get(0).getId());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookForGroupAndCompleted()  {

        User user = userRepository.save(new User("001111143"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2)));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"just do it too", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByGroupIdAndCompleted(group.getId(),false);
        assertEquals(2,list.size());
        lb1.setCompleted(true);
        lb1 = logBookRepository.save(lb1);
        list = logBookRepository.findAllByGroupIdAndCompleted(group.getId(),false);
        assertEquals(1,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUser()  {

        User user = userRepository.save(new User("001111144"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2), user.getId()));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"not assigned", DateTimeUtil.addHoursFromNow(2)));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserId(user.getId());
        assertEquals(1,list.size());
        lb2.setAssignedToUserId(user.getId());
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserId(user.getId());
        assertEquals(2,list.size());
    }

    @Test
    public void shouldSaveAndRetrieveLogBookAssignedToUserAndCompleted()  {

        User user = userRepository.save(new User("001111145"));
        Group group = groupRepository.save(new Group("test logbook", user));
        LogBook lb1 = logBookRepository.save(new LogBook(group.getId(),"just do it", DateTimeUtil.addHoursFromNow(2),user.getId()));
        LogBook lb2 = logBookRepository.save(new LogBook(group.getId(),"not assigned", DateTimeUtil.addHoursFromNow(2),user.getId()));
        List<LogBook> list = logBookRepository.findAllByAssignedToUserIdAndCompleted(user.getId(),true);
        assertEquals(0,list.size());
        lb2.setCompleted(true);
        lb2 = logBookRepository.save(lb2);
        list = logBookRepository.findAllByAssignedToUserIdAndCompleted(user.getId(),true);
        assertEquals(1,list.size());
    }

}
