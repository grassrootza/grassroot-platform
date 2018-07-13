package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.ServicesTestConfig;
import za.org.grassroot.services.task.EventBroker;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
public class EventBrokerTest {

   // @Rule
   // public OutputCapture capture = new OutputCapture();
    
    private Logger log = LoggerFactory.getLogger(EventBrokerTest.class);

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventLogRepository eventLogRepository;

    @Test
    public void shouldNotReturnOutstandingRSVPEventForSecondLevelUserAndParentGroupEvent() {
        User user = userRepository.save(new User("0825555511", null, null));
        Group grouplevel1 = groupRepository.save(new Group("rsvp level1",user));
        User userl1 = userRepository.save(new User("0825555512", null, null));
        grouplevel1.addMember(userl1, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        grouplevel1 = groupRepository.save(grouplevel1);
        Group grouplevel2 = groupRepository.save(new Group("rsvp level2",user));
        grouplevel2.setParent(grouplevel1);
        grouplevel2 = groupRepository.save(grouplevel2);
        User userl2 = userRepository.save(new User("0825555521", null, null));
        grouplevel2.addMember(userl2, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        grouplevel2 = groupRepository.save(grouplevel2);
        List<Event> outstanding =  eventBroker.getEventsNeedingResponseFromUser(userl2);
        assertNotNull(outstanding);
        assertEquals(0,outstanding.size());
    }

}
