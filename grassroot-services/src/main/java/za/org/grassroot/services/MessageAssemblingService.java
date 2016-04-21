package za.org.grassroot.services;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.dto.LogBookDTO;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.UserDTO;

import java.util.List;
import java.util.Map;

/**
 * Created by aakilomar on 8/24/15.
 */
public interface MessageAssemblingService {

    String createMeetingNotificationMessage(User user, EventDTO event);

    String createLogBookReminderMessage(User user, Group group, LogBook logBook);

    String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned);

    String createChangeMeetingNotificationMessage(User user, EventDTO event);

    String createCancelMeetingNotificationMessage(User user, EventDTO event);

    String createMeetingReminderMessage(User user, EventDTO event);

    String createVoteResultsMessage(User user, EventDTO event, double yes, double no, double abstain, double noReply);

    String createMeetingReminderMessage(String locale, User user, EventDTO event);

    String createMeetingRsvpTotalMessage(User user, EventDTO meeting, ResponseTotalsDTO responses);

    String createWelcomeMessage(String messageId, UserDTO userDTO);

    String createMeetingThankYouMessage(User user, EventDTO event);

    String createReplyFailureMessage(User user);

    /**
     * Assembles a message to notify group organizers that some people have joined via a join code.
     * @param user The user to receive the message
     * @param groupName The name of the group
     * @param numberJoined How many people joined
     * @param namesJoined Their names or phone numbers. Pass null if only want to display number.
     * @return An assembled string, in the user's language.
     */
    String createGroupJoinCodeUseMessage(User user, String groupName, int numberJoined, List<String> namesJoined);

}
