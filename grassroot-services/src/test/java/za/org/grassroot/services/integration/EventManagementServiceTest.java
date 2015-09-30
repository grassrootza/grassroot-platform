package za.org.grassroot.services.integration;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;

import static junit.framework.Assert.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class})
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class EventManagementServiceTest {

   // @Rule
   // public OutputCapture capture = new OutputCapture();
    
    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private EventManagementService eventManagementService;

    @Autowired
    private GroupManagementService groupManagementService;

    @Autowired
    UserRepository userRepository;

    @Autowired
    GroupRepository groupRepository;

    @Autowired
    EventRepository eventRepository;

    @Test
    public void shouldSaveEventWithNameUserAndGroup() {

        User userProfile = userManagementService.createUserProfile(new User("111222333", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222444", "111222555"));
        Event event = eventManagementService.createEvent("Drink till you drop", userProfile, group);
        assertEquals("Drink till you drop", event.getName());
        assertEquals(userProfile.getId(), event.getCreatedByUser().getId());
        assertEquals(group.getId(), event.getAppliesToGroup().getId());

    }

    @Test
    public void shouldSaveEventWithMinimumDataAndTriggerNotifications() {
        log.info("shouldSaveEventWithMinimumDataAndTriggerNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222555", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222666", "111222777"));
        Event event = eventManagementService.createEvent("Tell me about it", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place");
        event = eventManagementService.setDateTimeString(event.getId(),"31st 7pm");
        log.info("shouldSaveEventWithMinimumDataAndTriggerNotifications...done..." + event.toString());

    }

    //N.B. this method is not triggering queues so not actually working therefore no asserts
    @Test
    public void shouldTriggerAddAndChangeNotifications() {
        log.info("shouldTriggerAddAndChangeNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222556", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222667", "111222778"));
        Event event = eventManagementService.createEvent("Tell me about it 2", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place 2");
        event = eventManagementService.setDateTimeString(event.getId(),"31st 7pm");
        event = eventManagementService.setLocation(event.getId(),"New lekker place");
        log.info("shouldTriggerAddAndChangeNotifications...done..." + event.toString());

    }

    //N.B. this method is not triggering queues so not actually working therefore no asserts
    @Test
    public void shouldTriggerAddAndCancelNotifications() {
        log.info("shouldTriggerAddAndCancelNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222556", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222667", "111222778"));
        Event event = eventManagementService.createEvent("Tell me about it 2", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place 2");
        event = eventManagementService.setDateTimeString(event.getId(), "31 7pm");
        event = eventManagementService.cancelEvent(event.getId());
        log.info("shouldTriggerAddAndCancelNotifications...done..." + event.toString());

    }

    @Test
    public void shouldReturnOutstandingRSVPEventForSecondLevelUserAndParentGroupEvent() {
        User user = userRepository.save(new User("0825555511"));
        Group grouplevel1 = groupRepository.save(new Group("rsvp level1",user));
        User userl1 = userRepository.save(new User("0825555512"));
        grouplevel1.addMember(userl1);
        grouplevel1 = groupRepository.save(grouplevel1);
        Group grouplevel2 = groupRepository.save(new Group("rsvp level2",user));
        grouplevel2.setParent(grouplevel1);
        grouplevel2 = groupRepository.save(grouplevel2);
        User userl2 = userRepository.save(new User("0825555521"));
        grouplevel2.addMember(userl2);
        grouplevel2 = groupRepository.save(grouplevel2);
        Event event = eventRepository.save(new Event("test rsvp required",user,grouplevel1,true,true));
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.MINUTE,60);
        event.setEventStartDateTime(new Timestamp(calendar.getTime().getTime()));
        event = eventRepository.save(event);
        List<Event> outstanding =  eventManagementService.getOutstandingRSVPForUser(userl2);
        assertNotNull(outstanding);
        assertEquals(1,outstanding.size());
        assertEquals(event.getId(),outstanding.get(0).getId());
    }
    @Test
    public void shouldNotReturnOutstandingRSVPEventForSecondLevelUserAndParentGroupEvent() {
        User user = userRepository.save(new User("0825555511"));
        Group grouplevel1 = groupRepository.save(new Group("rsvp level1",user));
        User userl1 = userRepository.save(new User("0825555512"));
        grouplevel1.addMember(userl1);
        grouplevel1 = groupRepository.save(grouplevel1);
        Group grouplevel2 = groupRepository.save(new Group("rsvp level2",user));
        grouplevel2.setParent(grouplevel1);
        grouplevel2 = groupRepository.save(grouplevel2);
        User userl2 = userRepository.save(new User("0825555521"));
        grouplevel2.addMember(userl2);
        grouplevel2 = groupRepository.save(grouplevel2);
        Event event = eventRepository.save(new Event("test rsvp required",user,grouplevel1,true,true));
        //event either no date or in the past
        //Calendar calendar = Calendar.getInstance();
        //calendar.add(Calendar.MINUTE,60);
        //event.setEventStartDateTime(new Timestamp(calendar.getTime().getTime()));
        //event = eventRepository.save(event);
        List<Event> outstanding =  eventManagementService.getOutstandingRSVPForUser(userl2);
        assertNotNull(outstanding);
        assertEquals(0,outstanding.size());
    }

    //todo aakil test more scenarios , ie single level, multiple events etc

}
