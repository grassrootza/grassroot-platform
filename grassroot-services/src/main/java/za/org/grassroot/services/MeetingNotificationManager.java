package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;

import java.text.SimpleDateFormat;
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
    @Qualifier("servicesMessageSource")
    MessageSource messageSource;

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    @Override
    public String createMeetingNotificationMessage(User user, EventDTO event) {
        //TODO fix the locale resolver in config
        Locale locale = getUserLocale(user);
        String messageKey = event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }

    @Override
    public String createChangeMeetingNotificationMessage(User user, EventDTO event) {
        // TODO think if there's a simple way to work out which variable has changed and only send that
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage("sms.mtg.send.change", populateFields(user, event), locale);
    }

    @Override
    public String createCancelMeetingNotificationMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage("sms.mtg.send.cancel", populateFields(user, event), locale);
    }

    @Override
    public String createMeetingReminderMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage("sms.mtg.send.reminder", populateFields(user, event), locale);
    }

    private Locale getUserLocale(User user) {
        if (user.getLanguageCode() == null || user.getLanguageCode().trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(user.getLanguageCode());
        }

    }

    private String[] populateFields(User user, EventDTO event) {

        String salutation = (event.getAppliesToGroup().hasName()) ? event.getAppliesToGroup().getGroupName() : "GrassRoot";
        log.info("populateFields...user..." + user.getPhoneNumber() + "...event..." + event.getId() + "...version..." + event.getVersion());
        SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM, h:mm a");
        String dateString = "no date specified";
        if (event.getEventStartDateTime() != null) {
            dateString = sdf.format(event.getEventStartDateTime().getTime());
        }
        String[] eventVariables = new String[]{
                salutation,
                event.getCreatedByUser().nameToDisplay(),
                event.getName(),
                event.getEventLocation(),
                dateString
        };

        return eventVariables;

    }
}
