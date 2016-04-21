package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.dto.UserDTO;

import java.util.Map;

/**
 * Created by aakilomar on 8/24/15.
 */
public interface MeetingNotificationService {

    String createMeetingNotificationMessage(User user, EventDTO event);

     String createMeetingNotification(User user, EventDTO event, String channel);

    String createLogBookReminderMessage(User user, Group group, LogBook logBook);

    String createLogBookReminderMessage(User user, Group group, LogBook logBook, String channel);


    String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned);

    String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned, String channel);

    String createChangeMeetingNotificationMessage(User user, EventDTO event);

    String createChangeMeetingNotificationMessage(User user, EventDTO eventDTO, String channel);

    String createCancelMeetingNotificationMessage(User user, EventDTO event);

    String createCancelMeetingNotificationMessage(User user, EventDTO eventDTO, String channel);

    String createMeetingReminderMessage(User user, EventDTO event);

    String createMeetingReminderMessage(User user, EventDTO eventDTO, String channel);

    String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply);

    String createVoteResultsmessage(User user, EventDTO eventDTO, double yes, double no, double abstain, double noReply, String channel);

    /*
    Helper method to produce messages in different languages, for confirmation screens
     */
    String createMeetingReminderMessage(String locale, User user, EventDTO event);

    String createMeetingReminderMessage(String locale, User user, EventDTO event,String channel);

    String createWelcomeMessage(String messageId, UserDTO userDTO);

    String createWelcomeMessage(String messageId, UserDTO userDTO, String channel);

    String createReplyFailureMessage(User user);


}
