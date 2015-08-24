package za.org.grassroot.services.integration;

import junit.framework.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.MeetingNotificationService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/24/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@Transactional
public class NotificationSeviceTest {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    MeetingNotificationService meetingNotificationService;

    @Autowired
    ApplicationContext applicationContext;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    @Autowired
    GroupManagementService groupManagementService;

    //TODO this test as well as the corresponding test welcome.vm should be removed
    //     this is only useful now to test application configuration
    @Test
    public void shouldCallTemplateEngine() {
        String template = "welcome.vm";
        Map<String,Object> map = new HashMap<>();
        map.put("time", new Date());
        map.put("message","lekker my china");
        String message = meetingNotificationService.createMessageUsingVelocity("welcome.vm",map);
        Assert.assertEquals(true, message.contains("Message: lekker my china"));
    }

    @Test
    public void shouldGiveEnglishMessageForMeetingInvite() {
        String message = applicationContext.getMessage("meeting.invite",null, Locale.ENGLISH);
        Assert.assertEquals("You are invited to the",message);
    }

    @Test
    public void shouldGiveEnglishMeetingMessage() {
        User user = userRepository.save(new User("7777777"));
        Group group = groupManagementService.createNewGroup(user,"0828888888 0829999999");
        Event event = eventRepository.save(new Event("Drink till you drop",user,group));
        String message = meetingNotificationService.createMeetingNotificationMessage(user,event);
        Assert.assertEquals("You are invited to the Drink till you drop meeting by 7777777", message);
    }

    private String writeTempFile(String data) {
        String filename = "";
        try{

            //create a temp file
            File temp = File.createTempFile("tempfile", ".html");
            filename = temp.getAbsolutePath();
            //write it
            BufferedWriter bw = new BufferedWriter(new FileWriter(temp));
            bw.write(data);
            bw.close();

            log.info("Written to tempfile..." + filename);

        }catch(IOException e){

            e.printStackTrace();

        }
        return filename;
    }
}
