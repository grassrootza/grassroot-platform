package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.UserDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.FormatUtil;

import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 8/24/15.
 */
@Component
public class MessageAssemblingManager implements MessageAssemblingService {

    private Logger log = LoggerFactory.getLogger(MessageAssemblingManager.class);

    @Autowired
    @Qualifier("servicesMessageSourceAccessor")
    MessageSourceAccessor messageSourceAccessor;

    private static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

    @Override
    public String createEventInfoMessage(User user, Event event) {
        //TODO fix the locale resolver in config
        String messageKey = "";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.new";
        } else {
            messageKey = event.isRsvpRequired() ? "sms.mtg.send.new.rsvp" : "sms.mtg.send.new";

        }
        Locale locale = getUserLocale(user);
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventChangedMessage(User user, Event event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.change";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.change";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventCancelledMessage(User user, Event event) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.mtg.send.cancel";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.cancel";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createLogBookReminderMessage(User user, LogBook logBook) {
        Locale locale = getUserLocale(user);
        String[] args = populateLogBookFields(user, logBook);
        return messageSourceAccessor.getMessage("sms.logbook.reminder", args, locale);
    }

    @Override
    public String createLogBookInfoNotificationMessage(User target, LogBook logBook) {
        Locale locale = getUserLocale(target);
        String[] args = populateLogBookFields(target, logBook);
        String messageKey = logBook.isAllGroupMembersAssigned() ? "sms.logbook.new.assigned" : "sms.logbook.new.notassigned";
        return messageSourceAccessor.getMessage(messageKey, args, locale);
    }

    @Override
    public String createVoteResultsMessage(User user, Vote event, double yes, double no, double abstain, double noReply) {
        Locale locale = getUserLocale(user);
        String messageKey = "sms.vote.send.results";
        String[] args = populateEventFields(event, yes, no, abstain, noReply);
        return messageSourceAccessor.getMessage(messageKey, args, locale);
    }

    @Override
    public String createScheduledEventReminderMessage(User destination, Event event) {
        Locale locale = getUserLocale(destination);
        String messageKey = "sms.mtg.send.reminder";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.reminder";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses) {
        log.info("Constructing meeting total message ... with response object totals={}", responses.toString());
        String[] fields = new String[] { meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(shortDateFormatter),
                String.valueOf(responses.getYes()),
                String.valueOf(responses.getNo()),
                String.valueOf(responses.getNumberNoRSVP()),
                String.valueOf(responses.getNumberOfUsers()) };
        return messageSourceAccessor.getMessage("sms.meeting.rsvp.totals", fields, getUserLocale(user));
    }

    @Override
    public String createMeetingThankYourMessage(User target, Meeting meeting) {
        // sms.meeting.thankyou = {0}: Thank you for attending the meeting about {1} on {2}. Dial *134*1994# to create and join groups or call meetings.
        MeetingContainer parent = meeting.getParent();
        String prefix = (parent.getJpaEntityType().equals(JpaEntityType.GROUP) && ((Group) parent).hasName()) ?
                ((Group) parent).getGroupName() : "Grassroot";
        String[] fields = new String[]{prefix, meeting.getName(), meeting.getEventDateTimeAtSAST().format(shortDateFormatter)};
        return messageSourceAccessor.getMessage("sms.meeting.thankyou", fields, getUserLocale(target));
    }

    @Override
    public String createWelcomeMessage(String messageId, UserDTO userDTO) {
        return messageSourceAccessor.getMessage(messageId, populateWelcomeFields(userDTO), getUserLocale(userDTO.getLanguageCode()));
    }

    @Override
    public String createReplyFailureMessage(User user) {
        return messageSourceAccessor.getMessage("sms.reply.failure", "",getUserLocale(user));

    }

    @Override
    public String createGroupJoinCodeUseMessage(User user, String groupName, int numberJoined, List<String> namesJoined) {
        String message;
        String[] fields;
        if (namesJoined == null) {
            fields = new String[] { groupName, String.valueOf(numberJoined) };
            message = messageSourceAccessor.getMessage("sms.group.join.number", fields, getUserLocale(user));
        } else {
            String numbers = String.join(", ", namesJoined);
            fields = new String[] { groupName, numbers };
            message = messageSourceAccessor.getMessage("sms.group.join.named", fields, getUserLocale(user));
        }
        return message;
    }


    public Locale getUserLocale(User user) {
        return getUserLocale(user.getLanguageCode());
    }

    private Locale getUserLocale(String languageCode) {
        if (languageCode == null || languageCode.trim().equals("")) {
            return Locale.ENGLISH;
        } else {
            return new Locale(languageCode);
        }

    }

    public String[] populateEventFields(Event event) {
   		return populateEventFields(event, 0D, 0D, 0D, 0D);
   	}

    public String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply) {
        // todo: switch this to new name (may want a "hasName"/"getName" method defined on UidIdentifiable?
        String salutation = (((Group) event.getParent()).hasName()) ? ((Group) event.getParent()).getGroupName() : "Grassroot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = "no date specified";
        if (event.getEventStartDateTime() != null) {
            dateString = sdf.format(event.getEventDateTimeAtSAST());
        }

        String location = null;
        if (event instanceof Meeting) {
            Meeting meeting = (Meeting) event;
            location = meeting.getEventLocation();
        }

        String[] eventVariables = new String[]{
                salutation,
                event.getCreatedByUser().nameToDisplay(),
                event.getName(),
                location,
                dateString,
                FormatUtil.formatDoubleToString(yes),
                FormatUtil.formatDoubleToString(no),
                FormatUtil.formatDoubleToString(abstain),
                FormatUtil.formatDoubleToString(noReply)
        };

        return eventVariables;

    }

    private String[] populateLogBookFields(User target, LogBook logBook) {
        Group group = logBook.resolveGroup();
        String salutation = (group.hasName()) ? group.getGroupName() : "GrassRoot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = "no date specified";
        if (logBook.getActionByDate() != null) {
            dateString = sdf.format(logBook.getActionByDateAtSAST());
        }
        String[] variables = new String[]{
                salutation,
                logBook.getMessage(),
                dateString,
                target.getDisplayName()
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

    public String constructEventcancelledMessage(User user, Event event) {
   		Locale locale = getUserLocale(user);
   		String messageKey = "sms.mtg.send.cancel";
   		if (event.getEventType() == EventType.VOTE) {
   			messageKey = "sms.vote.send.cancel";
   		}
   		return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
   	}

    public String constructEventChangedMessage(User user, Event event) {
   		Locale locale = getUserLocale(user);

   		String messageKey = "sms.mtg.send.change";
   		if (event.getEventType() == EventType.VOTE) {
   			messageKey = "sms.vote.send.change";
   		}
   		return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
   	}

}
