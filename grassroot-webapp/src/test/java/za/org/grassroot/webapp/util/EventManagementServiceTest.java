package za.org.grassroot.webapp.util;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.MessagingConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import java.util.Arrays;
import java.util.logging.Logger;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class, MessagingConfig.class})
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

    @Test
    public void shouldSaveEventWithNameUserAndGroup() {

        User userProfile = userManagementService.createUserProfile(new User("111222333", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222444", "111222555"));
        Event event = eventManagementService.createEvent("Drink till you drop", userProfile, group);
        Assert.assertEquals("Drink till you drop",event.getName());
        Assert.assertEquals(userProfile.getId(),event.getCreatedByUser().getId());
        Assert.assertEquals(group.getId(),event.getAppliesToGroup().getId());

    }

    @Test
    public void shouldSaveEventWithMinimumDataAndTriggerNotifications() {
        log.info("shouldSaveEventWithMinimumDataAndTriggerNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222555", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222666", "111222777"));
        Event event = eventManagementService.createEvent("Tell me about it", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place");
        event = eventManagementService.setDateTimeString(event.getId(), "31 7pm");
        log.info("shouldSaveEventWithMinimumDataAndTriggerNotifications...done..." + event.toString());

    }

    @Test
    public void shouldTriggerAddAndChangeNotifications() {
        log.info("shouldTriggerAddAndChangeNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222556", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222667", "111222778"));
        Event event = eventManagementService.createEvent("Tell me about it 2", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place 2");
        event = eventManagementService.setDateTimeString(event.getId(), "31 7pm");
        event = eventManagementService.setLocation(event.getId(),"New lekker place");
        log.info("shouldTriggerAddAndChangeNotifications...done..." + event.toString());

    }
    @Test
    public void shouldTriggerAddAndCancelNotifications() {
        log.info("shouldTriggerAddAndCancelNotifications...starting...");
        User userProfile = userManagementService.createUserProfile(new User("111222556", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222667", "111222778"));
        Event event = eventManagementService.createEvent("Tell me about it 2", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place 2");
        event = eventManagementService.setDateTimeString(event.getId(),"31 7pm");
        event = eventManagementService.cancelEvent(event.getId());
        log.info("shouldTriggerAddAndCancelNotifications...done..." + event.toString());

    }

}
