package za.org.grassroot.services;

import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.*;
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

    String createTodoAssignedMessage(User user, Todo todo);

    String createTodoConfirmerMessage(User user, Todo todo);

    String createTodoVolunteerReceivedMessage(User target, TodoAssignment todoResponse);

    String createTodoInfoConfirmationMessage(TodoAssignment todoResponse);

    String createMultiOptionVoteResultsMessage(User user, Vote vote, Map<String, Long> optionsWithCount);

    String createScheduledEventReminderMessage(User destination, Event event);

    String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses);

    String createMeetingThankYourMessage(User target, Meeting meeting);

    String createTodoCancelledMessage(User user, Todo todo);

    String createTodoValidatedMessage(TodoAssignment todoAssignment);

    String createWelcomeMessage(String messageId, User user);

    String createSafetyEventMessage(User respondent, User requestor, Address address, boolean reminder);

    String createFalseSafetyEventActivationMessage(User requestor, long count);

    String createSafetyEventReportMessage(User user, User respondent, SafetyEvent safetyEvent, boolean respondedTo);

    String createBarringMessage(User requestor);

    String createGroupJoinRequestMessage(User user, GroupJoinRequest request);

    String createGroupJoinReminderMessage(User user, GroupJoinRequest request);

    String createGroupJoinResultMessage(GroupJoinRequest request, boolean approved);

    String createGroupJoinedMessage(User user, Group group);

    String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply);

    String createAndroidLinkSms(User user);

    List<String> getMessagesForGroups(List<Group> groups);

    String createGroupJoinCodeMessage(Group group);

    String createMultiLanguageMessage();

}
