package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationContext;
import org.springframework.context.MessageSource;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import org.springframework.ui.velocity.VelocityEngineUtils;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.FormatUtil;

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

    /*
    Although it says meeting using the same functions for voting!
     */
    @Override
    public String createMeetingNotificationMessage(User user, EventDTO event) {
        //TODO fix the locale resolver in config
        Locale locale = getUserLocale(user);
        String messageKey = "";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.new";
        } else {
            messageKey = event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";

        }
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }

    @Override
    public String createLogBookReminderMessage(User user, Group group, LogBookDTO logBookDTO) {
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage("sms.logbook.reminder", populateLogBookFields(user, group, logBookDTO), locale);
    }

    @Override
    public String createNewLogBookNotificationMessage(User user, Group group, LogBookDTO logBookDTO) {
        Locale locale = getUserLocale(user);
        if (logBookDTO.getAssignedToUserId() != null) {
            return messageSourceAccessor.getMessage("sms.logbook.new.assigned", populateLogBookFields(user, group, logBookDTO), locale);

        } else {
            return messageSourceAccessor.getMessage("sms.logbook.new.notassigned", populateLogBookFields(user, group, logBookDTO), locale);
        }
    }

    @Override
    public String createChangeMeetingNotificationMessage(User user, EventDTO event) {
        // TODO think if there's a simple way to work out which variable has changed and only send that
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.change";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.change";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }

    @Override
    public String createCancelMeetingNotificationMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.cancel";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.cancel";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }

    @Override
    public String createMeetingReminderMessage(User user, EventDTO event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.reminder";
        if (event.getEventType() == EventType.VOTE) messageKey = "sms.vote.send.reminder";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event), locale);
    }

    @Override
    public String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.vote.send.results";
        return messageSourceAccessor.getMessage(messageKey, populateFields(user, event, yes, no, abstain, noReply), locale);
    }

    @Override
    public String createMeetingReminderMessage(String locale, User user, EventDTO event) {
        log.info("Creating meeting reminder in this locale ..." + locale);
        return messageSourceAccessor.getMessage("sms.mtg.send.reminder", populateFields(user, event), locale);
    }

    @Override
    public String createWelcomeMessage(String messageId, UserDTO userDTO) {
        return messageSourceAccessor.getMessage(messageId, populateWelcomeFields(userDTO), getUserLocale(userDTO.getLanguageCode()));
    }

    @Override
    public String createReplyFailureMessage(User user) {
        return messageSourceAccessor.getMessage("sms.reply.failure", "",getUserLocale(user));

    }


    private Locale getUserLocale(User user) {
        return getUserLocale(user.getLanguageCode());
    }

    private Locale getUserLocale(String languageCode) {
        if (languageCode == null || languageCode.trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(languageCode);
        }

    }

    private String[] populateFields(User user, EventDTO event) {
        return populateFields(user, event, 0D, 0D, 0D, 0D);
    }

    private String[] populateFields(User user, EventDTO event, double yes, double no, double abstain, double noReply) {

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
                dateString,
                FormatUtil.formatDoubleToString(yes),
                FormatUtil.formatDoubleToString(no),
                FormatUtil.formatDoubleToString(abstain),
                FormatUtil.formatDoubleToString(noReply)
        };

        return eventVariables;

    }

    private String[] populateLogBookFields(User user, Group group, LogBookDTO logBookDTO) {
        String salutation = (group.hasName()) ? group.getGroupName() : "GrassRoot";
        SimpleDateFormat sdf = new SimpleDateFormat("EEE d MMM, h:mm a");
        String dateString = "no date specified";
        if (logBookDTO.getActionByDate() != null) {
            dateString = sdf.format(logBookDTO.getActionByDate());
        }
        String[] variables = new String[]{
                salutation,
                logBookDTO.getMessage(),
                dateString,
                user.getDisplayName()
        };
        return variables;
    }

    private String[] populateWelcomeFields(UserDTO userDTO) {
        return new String[] {
                userDTO.getDisplayName(),
                userDTO.getFirstName(),
                userDTO.getLastName(),
                userDTO.getPhoneNumber()
        };
    }
}
