package za.org.grassroot.services.integration;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.GrassrootServicesConfig;
import za.org.grassroot.core.GrassrootApplicationProfiles;

import java.util.Locale;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.*;

/**
 * @author Lesetse Kimwaga
 */

@RunWith(SpringRunner.class)
@ContextConfiguration(classes = TestContextConfig.class)
@Transactional
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class MessageSourceTest {

    @Autowired
    @Qualifier("servicesMessageSource")
    private MessageSource messageSource;

    @Test
    public void testMessagesInUKLocale() throws Exception {
        //Calling messageSource.getMessage throws exception if messages for Locale is not found
      String message =   messageSource.getMessage("sms.mtg.send.new.rsvp", new Object[]{"Grassroot", "Thabo", "Activism", "The Square", "15 October 2PM"}, Locale.UK);
      assertThat(message, is(not(isEmptyOrNullString())));

    }


    @Test
    public void testMessagesInEnglishLocale() throws Exception {
        //Calling messageSource.getMessage throws exception if messages for Locale is not found
        String message =   messageSource.getMessage("sms.mtg.send.new.rsvp", new Object[]{"Grassroot", "Thabo", "Activism", "The Square", "15 October 2PM"}, Locale.ENGLISH);
        assertThat(message, is(not(isEmptyOrNullString())));

    }

    @Test
    public void testMessagesInZuluLocale() throws Exception {
        //Calling messageSource.getMessage throws exception if messages for Locale is not found
        String message =   messageSource.getMessage("sms.mtg.send.new.rsvp", new Object[]{"Grassroot", "Thabo", "Activism", "The Square", "15 October 2PM"}, new Locale("zu"));
        assertThat(message, is(not(isEmptyOrNullString())));

    }
}
