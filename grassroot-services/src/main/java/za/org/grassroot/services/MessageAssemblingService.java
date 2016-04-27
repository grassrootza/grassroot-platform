package za.org.grassroot.services;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.dto.UserDTO;

import java.util.List;
import java.util.Locale;

/**
 * Created by aakilomar on 8/24/15.
 */
public interface MessageAssemblingService {

    String createLogBookReminderMessage(User user, Group group, LogBook logBook);

    String createNewLogBookNotificationMessage(User user, Group group, LogBook logBook, boolean assigned);

    String createVoteResultsMessage(User user, Vote event, double yes, double no, double abstain, double noReply);

    String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses);

    String createWelcomeMessage(String messageId, UserDTO userDTO);

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

    Locale getUserLocale(User user);

    String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply);
}
