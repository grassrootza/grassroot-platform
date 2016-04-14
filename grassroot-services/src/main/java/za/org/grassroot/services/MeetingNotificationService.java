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



    String createLogBookReminderMessage(User user, Group group, LogBook logBook);


    String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned);


    String createChangeMeetingNotificationMessage(User user, EventDTO event);


    String createCancelMeetingNotificationMessage(User user, EventDTO event);


    String createMeetingReminderMessage(User user, EventDTO event);


    String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply);


    /*
    Helper method to produce messages in different languages, for confirmation screens
     */
    String createMeetingReminderMessage(String locale, User user, EventDTO event);


    String createWelcomeMessage(String messageId, UserDTO userDTO);


    String createReplyFailureMessage(User user);


}
