package za.org.grassroot.services;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.geo.Address;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventSpecialForm;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.FormatUtil;
import za.org.grassroot.integration.experiments.ExperimentBroker;
import za.org.grassroot.integration.experiments.VariationAssignment;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Created by aakilomar on 8/24/15.
 */
@Slf4j
@Component
public class MessageAssemblingManager implements MessageAssemblingService {

    public static final DateTimeFormatter shortDateFormatter = DateTimeFormatter.ofPattern("EEE, d/M");

    private static final DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");

    @Value("${grassroot.optimizely.active:false}")
    private boolean optimizelyActive;

    private final MessageSourceAccessor messageSourceAccessor;
    private final ExperimentBroker experimentBroker;

    private Map<TodoType, String> todoMsgKeyRootMap;
    private static final String defaultTodoKey = "sms.todo.new.notassigned";

    @Autowired
    public MessageAssemblingManager(@Qualifier("servicesMessageSourceAccessor") MessageSourceAccessor messageSourceAccessor, ExperimentBroker experimentBroker) {
        this.messageSourceAccessor = messageSourceAccessor;
        this.experimentBroker = experimentBroker;
    }

    @PostConstruct
    public void init() {
        todoMsgKeyRootMap= new HashMap<>();
        todoMsgKeyRootMap.put(TodoType.INFORMATION_REQUIRED, "text.todo.information");
        todoMsgKeyRootMap.put(TodoType.VALIDATION_REQUIRED, "text.todo.conf.assigned");
        todoMsgKeyRootMap.put(TodoType.ACTION_REQUIRED, "text.todo.action");
        todoMsgKeyRootMap.put(TodoType.VOLUNTEERS_NEEDED, "text.todo.volunteer");
    }

    @Override
    public String createEventInfoMessage(User user, Event event) {
        VariationAssignment assignment = !optimizelyActive ? null : experimentBroker.assignUser("test_experiment_meetings", user.getUid(), null);
        String messageKey;
        if (event instanceof Vote) {
            messageKey = event.hasImage() ? "text.vote.send.image" :
                    EventSpecialForm.MASS_VOTE.equals(event.getSpecialForm()) ? "text.vote.send.mass" : "sms.vote.send.new";
        } else if (event.hasImage()) {
            messageKey = "sms.mtg.send.image";
        } else if (event.isHighImportance() || (assignment != null && assignment.equals(VariationAssignment.EXPERIMENT))) {
            messageKey = "sms.mtg.send.special";
        } else {
            messageKey = "sms.mtg.send.new.rsvp";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), user.getLocale());
    }

    @Override
    public String createEventChangedMessage(User user, Event event) {
        Locale locale = user.getLocale();
        String messageKey = "sms.mtg.send.change";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.change";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventCancelledMessage(User user, Event event) {
        Locale locale = user.getLocale();
        String messageKey = "sms.mtg.send.cancel";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.cancel";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createEventResponseMessage(User user, Event event, EventRSVPResponse rsvpResponse) {
        Meeting meeting = (Meeting) event;
        String messageKey;
        String salutation = (( meeting.getParent()).hasName()) ? ((Group) meeting.getParent()).getGroupName() : "Grassroot";
        DateTimeFormatter sdf = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a");
        String dateString = sdf.format(meeting.getEventDateTimeAtSAST());
        String[] field = {salutation, user.nameToDisplay(), meeting.getName(), dateString, meeting.getEventLocation()};
        if (rsvpResponse.equals(EventRSVPResponse.YES)) {
            messageKey = "sms.mtg.attendance.yes";
        } else if (rsvpResponse.equals(EventRSVPResponse.NO)) {
            messageKey = "sms.mtg.attendance.no";
        } else {
            messageKey = "sms.mtg.attendance.maybe";
        }
        return messageSourceAccessor.getMessage(messageKey, field, user.getLocale());
    }

    @Override
    public String createTodoReminderMessage(User user, Todo todo) {
        Locale locale = user.getLocale();
        String[] args = new String[] { todo.getParent().getName(), todo.getCreatedByUser().getName(),
                todo.getMessage(), sdf.format(todo.getActionByDateAtSAST())};
        final String msgKey = todoMsgKeyRootMap.getOrDefault(todo.getType(), defaultTodoKey) + ".reminder";
        return messageSourceAccessor.getMessage(msgKey, args, locale);
    }

    @Override
    public String createTodoAssignedMessage(User user, Todo todo) {
        final Locale locale = user.getLocale();
        final String[] todoFields = new String[] {
                todo.getAncestorGroup().getName(),
                todo.getCreatedByUser().getName(),
                todo.getMessage(),
                sdf.format(todo.getActionByDateAtSAST()),
                todo.getImageUrl()
        };
        final String messageKey = todoMsgKeyRootMap.getOrDefault(todo.getType(), defaultTodoKey)
                + (todo.hasImage() ? ".image" : ".new"
                + (todo.getActionByDate().isBefore(Instant.now().plus(1, ChronoUnit.HOURS)) ? ".instant" : ""));
        log.debug("generating todo message, with key: {}, for locale: {}", messageKey, locale);
        return messageSourceAccessor.getMessage(messageKey, todoFields, locale);
    }

    @Override
    public String createTodoConfirmerMessage(User user, Todo todo) {
        final Locale locale = user.getLocale();
        final String[] todoFields = new String[] {
                todo.getAncestorGroup().getName(),
                todo.getCreatedByUser().getName(),
                todo.getMessage(),
                todo.getImageUrl()
        };
        return messageSourceAccessor.getMessage("text.todo.conf.confirms." + (todo.hasImage() ? "image" : "new"), todoFields, locale);
    }

    @Override
    public String createTodoVolunteerReceivedMessage(User target, TodoAssignment todoResponse) {
        final Locale locale = target.getLocale();
        final String[] fields = new String[] {
                todoResponse.getUser().getName(),
                todoResponse.getUser().getNationalNumber(),
                todoResponse.getTodo().getName()
        };
        return messageSourceAccessor.getMessage("text.todo.volunteer.responded", fields, locale);
    }

    @Override
    public String createTodoInfoConfirmationMessage(TodoAssignment todoResponse) {
        final User user = todoResponse.getUser();
        final Locale locale = user.getLocale();
        final String[] fields = new String[] {
                todoResponse.getResponseText(), todoResponse.getTodo().getName()
        };
        return messageSourceAccessor.getMessage("text.todo.information.responded", fields, locale);
    }

    @Override
    public String createMultiOptionVoteResultsMessage(User user, Vote vote, Map<String, Long> optionsWithCount) {
        Locale locale = user.getLocale();
        String messagePrefix = messageSourceAccessor.getMessage("sms.vote.send.results.prefix",
                new String[] { vote.getAncestorGroup().getName(), vote.getName() }, locale);
        StringBuilder sb = new StringBuilder(messagePrefix);
        optionsWithCount.forEach((option, count) -> {
            sb.append(", ").append(option).append(" = ").append(String.valueOf(count));
        });
        sb.append(", ").append(
                messageSourceAccessor.getMessage("sms.vote.send.results.noreply",
                new String[] { countNoReply(optionsWithCount, vote) }));
        return sb.toString();
    }

    // todo : watch TX counts here (on object graph)
    private String countNoReply(Map<String, Long> count, Vote vote) {
        long totalVotes = count.values().stream().mapToLong(Long::longValue).sum();
        return String.valueOf(vote.getAllMembers().size() - totalVotes);
    }

    @Override
    public String createScheduledEventReminderMessage(User destination, Event event) {
        Locale locale = destination.getLocale();
        String messageKey = "sms.mtg.send.reminder";
        if (event.getEventType() == EventType.VOTE) {
            messageKey = "sms.vote.send.reminder";
        }
        return messageSourceAccessor.getMessage(messageKey, populateEventFields(event), locale);
    }

    @Override
    public String createMeetingRsvpTotalMessage(User user, Meeting meeting, ResponseTotalsDTO responses) {
        log.info("Constructing meeting total message ... with response object totals={}", responses.toString());
        String[] fields = new String[]{meeting.getName(),
                meeting.getEventDateTimeAtSAST().format(shortDateFormatter),
                String.valueOf(responses.getYes()),
                String.valueOf(responses.getNo()),
                String.valueOf(responses.getNumberNoRSVP()),
                String.valueOf(responses.getNumberOfUsers())};
        return messageSourceAccessor.getMessage("sms.meeting.rsvp.totals", fields, user.getLocale());
    }

    @Override
    public String createMeetingThankYourMessage(User target, Meeting meeting) {
        // sms.meeting.thankyou = {0}: Thank you for attending the meeting about {1} on {2}. Dial *134*1994# to create and join groups or call meetings.
        MeetingContainer parent = meeting.getParent();
        String prefix = (parent.getJpaEntityType().equals(JpaEntityType.GROUP) && ((Group) parent).hasName()) ?
                ((Group) parent).getGroupName() : "Grassroot";
        String[] fields = new String[]{prefix, meeting.getName(), meeting.getEventDateTimeAtSAST().format(shortDateFormatter)};
        return messageSourceAccessor.getMessage("sms.meeting.thankyou", fields, target.getLocale());
    }

    @Override
    public String createTodoCancelledMessage(User user, Todo todo) {
        Locale locale = user.getLocale();
        String[] args = new String[] {
                todo.getParent().getName(),
                todo.getCreatedByUser().getName(),
                todo.getMessage() };
        return messageSourceAccessor.getMessage("text.todo.cancelled", args, locale);
    }

    @Override
    public String createTodoValidatedMessage(TodoAssignment todoAssignment) {
        final Todo todo = todoAssignment.getTodo();
        final Locale locale = todo.getCreatedByUser().getLocale();
        final String[] fields = new String[] {
                todo.getAncestorGroup().getName(),
                todoAssignment.getUser().getName(),
                todo.getMessage()
        };
        return messageSourceAccessor.getMessage("text.todo.conf.confirms.notify", fields, locale);
    }

    @Override
    public String createWelcomeMessage(String messageId, User user) {
        String[] welcomeFields = {
                user.getDisplayName(),
                user.getFirstName(),
                user.getLastName(),
                user.getPhoneNumber()
        };
        return messageSourceAccessor.getMessage(messageId, welcomeFields, user.getLocale());
    }

    @Override
    public String createSafetyEventMessage(User respondent, User requestor, Address address, boolean reminder) {

        String[] fields;
        String message;
        if (address != null) {
            fields = new String[]{requestor.nameToDisplay(), address.getHouse(), address.getStreet(), address.getNeighbourhood()};
            message = (reminder) ? messageSourceAccessor.getMessage("sms.safety.reminder", fields, requestor.getLocale()) :
                    messageSourceAccessor.getMessage("sms.safety.new", fields, requestor.getLocale());
        } else {
            fields = new String[]{requestor.nameToDisplay()};
            message = (reminder) ? messageSourceAccessor.getMessage("sms.safety.reminder.nolocation", fields, requestor.getLocale()) :
                    messageSourceAccessor.getMessage("sms.safety.new.nolocation", fields, requestor.getLocale());
        }
        return message;
    }

    @Override
    public String createFalseSafetyEventActivationMessage(User requestor, long count) {
        return messageSourceAccessor.getMessage("sms.safety.false", new String[]{
                String.valueOf(count)}, requestor.getLocale());
    }

    @Override
    public String createSafetyEventReportMessage(User user, User respondent, SafetyEvent safetyEvent, boolean respondedTo) {
        String fields[];
        String message;
        if (respondedTo) {
            fields = new String[]{respondent.nameToDisplay(), safetyEvent.getActivatedBy().getDisplayName()};
            message = (!safetyEvent.isFalseAlarm()) ? messageSourceAccessor.getMessage("sms.safety.valid", fields, user.getLocale()) :
                    messageSourceAccessor.getMessage("sms.safety.invalid", fields, user.getLocale());
        } else {
            //todo trigger this message after an hour if the safetyevent was not responded to
            fields = new String[]{safetyEvent.getActivatedBy().nameToDisplay()};
            message = messageSourceAccessor.getMessage("sms.safety.noresponse", fields, user.getLocale());
        }
        return message;
    }

    @Override
    public String createBarringMessage(User requestor) {
        return messageSourceAccessor.getMessage("sms.safety.barred", "", requestor.getLocale());
    }

    @Override
    public String createGroupJoinRequestMessage(User user, GroupJoinRequest request) {
        String[] fields = {request.getRequestor().getDisplayName(), request.getGroup().getGroupName()};
        return messageSourceAccessor.getMessage("sms.group.join.request", fields, user.getLocale());
    }

    @Override
    public String createGroupJoinReminderMessage(User user, GroupJoinRequest request) {
        String[] fields = {request.getRequestor().getDisplayName(), request.getGroup().getGroupName()};
        return messageSourceAccessor.getMessage("sms.group.join.request.reminder", fields, user.getLocale());
    }

    @Override
    public String createGroupJoinResultMessage(GroupJoinRequest request, boolean approved) {
        final String[] fields = { request.getGroup().getName() };
        return approved ? messageSourceAccessor.getMessage("sms.group.join.approved", fields, request.getRequestor().getLocale())
                : messageSourceAccessor.getMessage("sms.group.join.denied", fields, request.getRequestor().getLocale());
    }

    @Override
    public String createGroupJoinedMessage(User user, Group group) {
        return messageSourceAccessor.getMessage("text.group.join.word.done", new String[]{
                group.getName(), group.getGroupTokenCode()}, user.getLocale());
    }


    @Override
    public String createReplyFailureMessage(User user) {
        return messageSourceAccessor.getMessage("sms.reply.failure", "", user.getLocale());

    }

    @Override
    public String createGroupJoinCodeUseMessage(User user, String groupName, int numberJoined, List<String> namesJoined) {
        String message;
        String[] fields;
        if (namesJoined == null) {
            fields = new String[]{groupName, String.valueOf(numberJoined)};
            message = messageSourceAccessor.getMessage("sms.group.join.number", fields, user.getLocale());
        } else {
            String numbers = String.join(", ", namesJoined);
            fields = new String[]{groupName, numbers};
            message = messageSourceAccessor.getMessage("sms.group.join.named", fields, user.getLocale());
        }
        return message;
    }

    @Override
    public String createAndroidLinkSms(User user) {
        return messageSourceAccessor.getMessage("sms.link.android", user.getLocale());
    }

    private String[] populateEventFields(Event event) {
        return populateEventFields(event, 0D, 0D, 0D, 0D);
    }

    public String[] populateEventFields(Event event, double yes, double no, double abstain, double noReply) {
        String salutation = ((event.getParent()).hasName()) ? event.getParent().getName() : "Grassroot";
        String dateString = sdf.format(event.getEventDateTimeAtSAST());

        String location = null;
        if (event instanceof Meeting) {
            Meeting meeting = (Meeting) event;
            location = meeting.getEventLocation();
        }

        String subject = event.getName();
        subject = (subject.contains("&")) ? subject.replace("&", "and") : subject;

        Membership creatingMember = event.getAncestorGroup().getMembership(event.getCreatedByUser());
        String userAlias = creatingMember != null ? creatingMember.getDisplayName() : event.getCreatedByUser().getDisplayName();

        String[] eventVariables;
        if (event.hasImage()) {
            eventVariables = new String[] {
                    salutation,
                    userAlias,
                    subject,
                    location,
                    dateString,
                    event.getImageUrl()
            };
        } else {
            eventVariables = new String[]{
                    salutation,
                    userAlias,
                    subject,
                    location,
                    dateString,
                    FormatUtil.formatDoubleToString(yes),
                    FormatUtil.formatDoubleToString(no),
                    FormatUtil.formatDoubleToString(abstain),
                    FormatUtil.formatDoubleToString(noReply)
            };
        }

        return eventVariables;

    }

    private String[] populateTodoFields(Todo todo) {
        Group group = todo.getAncestorGroup();
        String salutation = (group.hasName()) ? group.getGroupName() : "Grassroot";
        String dateString = sdf.format(todo.getActionByDateAtSAST());
        String assignment = (todo.getAssignedMembers().size() == 1) ?
                todo.getAssignedMembers().iterator().next().getDisplayName() : String.valueOf(todo.getAssignedMembers().size());

        return new String[]{
                salutation,
                todo.getMessage(),
                dateString,
                assignment
        };
    }

    @Override
    public List<String> getMessagesForGroups(List<Group> groups){
        List<String> messages = new ArrayList<>();

        StringBuilder completedMessage = new StringBuilder("Join codes:");
        String msgSegmentHolder;

        for(Group group : groups) {
            if( group.hasValidGroupTokenCode()){
                msgSegmentHolder = group.getGroupName() + "-" +group.getGroupTokenCode() +",";
                if((msgSegmentHolder.length() + completedMessage.length()) > 160){
                    messages.add(completedMessage.toString());
                    completedMessage = new StringBuilder("Join codes:");
                }
                completedMessage.append(msgSegmentHolder);
            }
        }
        return messages;
    }

    @Override
    public String createGroupJoinCodeMessage(Group group) {
        String[] fields = new String[]{group.getGroupName(),group.getGroupTokenCode()};
        return messageSourceAccessor.getMessage("text.group.join.code",
                fields, group.getCreatedByUser().getLocale());
    }

    @Override
    public String createMultiLanguageMessage() {
        // by definition, only makes sense in English (is multi-lingual in any case)
        return messageSourceAccessor.getMessage("text.languages.alert", Locale.ENGLISH);
    }

}
