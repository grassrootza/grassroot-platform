package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.services.ServicesTestConfig;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.Locale;
import java.util.logging.Logger;

import static org.junit.Assert.assertTrue;

/**
 * Created by aakilomar on 8/24/15.
 */
@RunWith(SpringRunner.class) @DataJpaTest
@ContextConfiguration(classes = ServicesTestConfig.class)
public class NotificationServiceTest {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    private MessageSourceAccessor messageSourceAccessor;

    @Test
    public void shouldGetMessageTemplate() {
        String template = "welcome.test";
        String[] msgObjects = new String[]{
                Instant.now().toString(),
                "Is this working?"
        };
        String message = messageSourceAccessor.getMessage(template, msgObjects, Locale.ENGLISH);
        assertTrue(message.contains("Message: Is this working?"));
    }

    @Test
    public void shouldGiveEnglishMessageForMeetingInvite() {
        String message = messageSourceAccessor.getMessage("sms.mtg.send.new.rsvp", new Object[]{"Grassroot", "Thabo", "Activism", "The Square", "15 October 2PM"}, Locale.ENGLISH);
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
