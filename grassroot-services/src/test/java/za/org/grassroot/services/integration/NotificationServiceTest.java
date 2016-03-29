package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassRootServicesConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.MeetingNotificationService;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Date;
import java.util.Locale;
import java.util.logging.Logger;

import static junit.framework.Assert.assertTrue;

/**
 * Created by aakilomar on 8/24/15.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = GrassRootServicesConfig.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class NotificationServiceTest {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    MeetingNotificationService meetingNotificationService;

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    @Autowired
    UserRepository userRepository;

    @Autowired
    EventRepository eventRepository;

    //TODO this test as well as the corresponding test welcome.vm should be removed
    //     this is only useful now to test application configuration
    @Test
    public void shouldGetMessageTemplate() {
        String template = "welcome.test";
        String[] msgObjects = new String[]{
                new Date().toString(),
                "Is this working?"
        };
        String message = messageSourceAccessor.getMessage(template, msgObjects, Locale.ENGLISH);
        assertTrue(message.contains("Message: Is this working?"));
    }

    @Test
    public void shouldGiveEnglishMessageForMeetingInvite() {
        String message = messageSourceAccessor.getMessage("sms.mtg.send.new", new Object[]{"GrassRoot", "Thabo", "Activism", "The Square", "15 October 2PM"}, Locale.ENGLISH);

    }

    @Test
    public void shouldGiveEnglishMeetingMessage() {
        /*User  user  = userRepository.save(new User("27817770000"));
        Group group = groupManagementService.createNewGroup(user, Arrays.asList("0828888888", "0829999999"), false);
        Event event = eventRepository.save(new Event("Drink till you drop", user, group));
        event.setEventLocation("Ellispark");
        String message = meetingNotificationService.createMeetingNotificationMessage(user, new EventDTO(event));
        log.info("shouldGiveEnglishMeetingMessage..." + message);
        assertEquals("GrassRoot : 081 777 0000 has called a meeting about Drink till you drop, at Ellispark, on no date specified",
                message);*/
    }

    @Test
    public void shouldGiveEnglishChangeMeetingMessage() {
        /*User user = userRepository.save(new User("27817770000"));
        Group group = groupManagementService.createNewGroup(user, Arrays.asList("0828888888", "0829999999"), false);
        Event event = eventRepository.save(new Event("Drink till you drop",user,group));
        event.setEventLocation("Ellispark");
        String message = meetingNotificationService.createChangeMeetingNotificationMessage(user, new EventDTO(event));
        log.info("shouldGiveEnglishMeetingMessage..." + message);
        assertEquals("GrassRoot : 081 777 0000 has changed the meeting about Drink till you drop, it will now be at Ellispark, on no date specified",
                     message);*/
    }

    @Test
    public void shouldGiveEnglishCancelMeetingMessage() {
        /* User user = userRepository.save(new User("27817770000"));
        Group group = groupManagementService.createNewGroup(user, Arrays.asList("0828888888", "0829999999"), false);
        Event event = eventRepository.save(new Event("Drink till you drop",user,group));
        event.setEventLocation("Ellispark");
        String message = meetingNotificationService.createCancelMeetingNotificationMessage(user,new EventDTO(event));
        log.info("shouldGiveEnglishMeetingMessage..." + message);
        assertEquals("GrassRoot : 081 777 0000 has cancelled the meeting about Drink till you drop, at Ellispark, on no date specified",
                     message);*/
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
