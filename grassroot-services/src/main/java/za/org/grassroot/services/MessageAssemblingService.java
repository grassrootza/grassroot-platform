package za.org.grassroot.services;

import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.List;
import java.util.Map;

/**
 * Created by aakilomar on 8/24/15.
 */
public interface MessageAssemblingService {

    String createEventInfoMessage(User user, Event event);

    String createEventChangedMessage(User user, Event event);

    String createEventCancelledMessage(User user, Event event);

    String createEventResponseMessage(User user, Event event, EventRSVPResponse rsvpResponse);

    String createTodoReminderMessage(User user, Todo todo);

    String createTodoRecordedNotificationMessage(User target, Todo todo);

    String createTodoUpdateNotificationMessage(User target, Todo todo);

    String createVoteResultsMessage(User user, Vote event, double yes, double no, double abstain, double noReply);

    String createMultiOptionVoteResultsMessage(User user, Vote vote, Map<String, Long> optionsWithCount);

    String createScheduledEventReminderMessage(User destination, Event event);

    String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses);

    String createMeetingThankYourMessage(User target, Meeting meeting);

    String createWelcomeMessage(String messageId, User user);

    String createMeetingAttendanceConfirmationMessage(User organiser, User member, EventLog eventLog);

    String createSafetyEventMessage(User respondent, User requestor, Address address, boolean reminder);

    String createFalseSafetyEventActivationMessage(User requestor, long count);

    String createSafetyEventReportMessage(User user, User respondent, SafetyEvent safetyEvent, boolean respondedTo);

    String createBarringMessage(User requestor);

    String createGroupJoinRequestMessage(User user, GroupJoinRequest request);

    String createGroupJoinReminderMessage(User user, GroupJoinRequest request);

    String createGroupJoinResultMessage(GroupJoinRequest request, boolean approved);

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

    String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply);

    String createAndroidLinkSms(User user);
}
