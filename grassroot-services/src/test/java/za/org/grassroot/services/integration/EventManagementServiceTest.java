package za.org.grassroot.services.integration;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;

import java.util.Arrays;
import java.util.logging.Logger;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = {GrassRootServicesConfig.class})
@Transactional
public class EventManagementServiceTest {

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

        User userProfile = userManagementService.createUserProfile(new User("111222555", "aap1"));
        Group group = groupManagementService.createNewGroup(userProfile, Arrays.asList("111222666", "111222777"));
        Event event = eventManagementService.createEvent("Tell me about it", userProfile, group);
        event = eventManagementService.setLocation(event.getId(), "Lekker place");
        event = eventManagementService.setDay(event.getId(),"31");
        event = eventManagementService.setTime(event.getId(),"7pm");
        log.info("shouldSaveEventWithMinimumDataAndTriggerNotifications..." + event.toString());

    }
}
