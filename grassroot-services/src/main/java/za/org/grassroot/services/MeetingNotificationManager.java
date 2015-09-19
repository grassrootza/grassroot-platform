package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;

import java.util.Locale;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/24/15.
 */
@Component
public class MeetingNotificationManager implements MeetingNotificationService {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    ApplicationContext applicationContext;

    @Override
    public String createMeetingNotificationMessage(User user, Event event) {
        //TODO fix the locale resolver in config
        Locale locale = getUserLocale(user);
        return applicationContext.getMessage("sms.mtg.send.new", populateFields(user, event), locale);
    }

    @Override
    public String createChangeMeetingNotificationMessage(User user, Event event) {
        // TODO fix the locale resolver in config
        // TODO think if there's a simple way to work out which variable has changed and only send that
        Locale locale = getUserLocale(user);
        return applicationContext.getMessage("sms.mtg.send.change", populateFields(user, event), locale);
    }

    @Override
    public String createCancelMeetingNotificationMessage(User user, Event event) {
        //TODO fix the locale resolver in config
        Locale locale = getUserLocale(user);
        return applicationContext.getMessage("sms.mtg.send.cancel", populateFields(user, event), locale);
    }

    private Locale getUserLocale(User user) {
        if (user.getLanguageCode() == null || user.getLanguageCode().trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(user.getLanguageCode());
        }

    }

    private String[] populateFields(User user, Event event) {

        String salutation = (event.getAppliesToGroup().hasName()) ? event.getAppliesToGroup().getGroupName() : "GrassRoot";

        String[] eventVariables = new String[]{
                salutation,
                event.getCreatedByUser().displayName(),
                event.getName(),
                event.getEventLocation(),
                event.getDateTimeString()
        };

        return eventVariables;

    }
}
