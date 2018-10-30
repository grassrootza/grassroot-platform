package za.org.grassroot.scheduling;

import com.google.common.collect.ImmutableMap;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.Todo_;
import za.org.grassroot.core.events.AlterConfigVariableEvent;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.TodoSpecifications;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.VoteBroker;

import javax.annotation.PostConstruct;
import javax.persistence.criteria.Join;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 10/5/15.
 */
@Component @Slf4j
public class ScheduledTasks implements ApplicationListener<AlterConfigVariableEvent> {

    private Map<String, String> configDefaults = ImmutableMap.of(
            "tasks.reminders.unpaid.send", "false",
            "meetings.thankyou.unpaid.send", "false");
    private Map<String, String> configVariables = new HashMap<>();

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Autowired
    private ConfigRepository configRepository;

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private VoteBroker voteBroker;

    @Autowired
    private TodoBroker todoBroker;

    @Autowired
    private SafetyEventBroker safetyEventBroker;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private TodoRepository todoRepository;

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @PostConstruct
    public void init() {
        configDefaults.forEach((key, defaultValue) -> configVariables.put(key,
                configRepository.findOneByKey(key).map(ConfigVariable::getValue).orElse(defaultValue)));
        log.info("Populated scheduled task config variable map : {}", configVariables);
    }

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        final boolean sendUnpaidEventReminders = Boolean.parseBoolean(configVariables.getOrDefault("tasks.reminders.unpaid.send", "false"));
        List<Event> events = eventRepository.findEventsForReminders(Instant.now())
                .stream().filter(event -> sendUnpaidEventReminders || event.getAncestorGroup().robustIsPaidFor())
                .collect(Collectors.toList());

        log.debug("Sending scheduled reminders for {} event(s)", events.size());

        for (Event event : events) {
            try {
                eventBroker.sendScheduledReminder(event.getUid());
            } catch (Exception e) {
                log.error("Error while sending scheduled reminder of event " + event + ": " + e.getMessage(), e);
            }
        }

        log.info("Sending scheduled reminders...done");
    }

    @Scheduled(fixedRate = 300000)
    public void sendSafetyReminders() {
        List<SafetyEvent> safetyEvents = safetyEventRepository.findSafetyEvents(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());
        log.info("Sending out safety reminders");
        if (safetyEvents != null) {
            safetyEvents.forEach(e -> safetyEventBroker.sendReminders(e.getUid()));
        }
    }

    @Scheduled(fixedRate = 60000) //runs every 1 minute
    public void sendUnsentVoteResults() {
        log.debug("Checking for unsent votes ...");
        voteRepository
                .findUnsentVoteResults(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now())
                .forEach(vote -> voteBroker.calculateAndSendVoteResults(vote.getUid()));
    }

    @Scheduled(cron = "0 0 16 * * *") // runs at 4pm (=6pm SAST) every day
    public void sendMeetingThankYous() {
        LocalDate yesterday = LocalDate.now().minus(1, ChronoUnit.DAYS);
        Instant start = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MIN), getSAST());
        Instant end = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MAX), getSAST());

        Specification<Event> mtgCharacteristics = (root, query, cb) -> cb.and(cb.isFalse(root.get("canceled")),
                cb.isTrue(root.get("rsvpRequired")), cb.between(root.get("eventStartDateTime"), start, end));
        Specification<Event> ancestorGroupPaidFor = (root, query, cb) -> {
            Join<Meeting, Group> groupJoin = root.join("ancestorGroup");
            return cb.isTrue(groupJoin.get("paidFor"));
        };

        final boolean sendForUnpaidGroups = Boolean.parseBoolean(configVariables.getOrDefault("meetings.thankyou.unpaid.send", "false"));
        Specification<Event> specs = sendForUnpaidGroups ? mtgCharacteristics : mtgCharacteristics.and(ancestorGroupPaidFor);
        List<Event> meetings = meetingRepository.findAll(specs);

        log.info("Sending out meeting thank you for {} meetings", meetings.size());

        for (Event meeting : meetings) {
            try {
                eventBroker.sendMeetingAcknowledgements(meeting.getUid());
            } catch (Exception ex) {
                log.error("Error while sending acknowledgments for meeting " + meeting + ": " + ex.getMessage(), ex);
            }
        }
    }

    @Scheduled(fixedRate = 3600000) // runs every hour
    public void sendMeetingRSVPsToDate() {
        // since the scheduled job runs once an hour, check for meetings created two days ago, in an hour interval
        Instant start = Instant.now().minus(48, ChronoUnit.HOURS);
		Instant end = start.minus(1, ChronoUnit.HOURS);

        List<Meeting> meetings = meetingRepository.meetingsForResponseTotals(Instant.now(), start, end)
                .stream().filter(meeting -> meeting.getAncestorGroup().isPaidFor()).collect(Collectors.toList());

		log.info("Sending out RSVP totals for {} meetings", meetings.size());
        for (Meeting meeting : meetings) {
            try {
                eventBroker.sendMeetingRSVPsToDate(meeting.getUid());
            } catch (Exception e) {
                log.error("Error while sending responses for meeting " + meeting + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendTodoReminders() {
        final boolean sendUnpaidTodoReminders = Boolean.parseBoolean(configVariables.getOrDefault("tasks.reminders.unpaid.send", "false"));

        Specification<Todo> specs = TodoSpecifications.notCancelled()
                .and(TodoSpecifications.remindersLeftToSend())
                .and(TodoSpecifications.reminderTimeBefore(Instant.now()))
                .and(TodoSpecifications.actionByDateAfter(Instant.now()))
                .and(TodoSpecifications.todoNotCompleted());

        if (!sendUnpaidTodoReminders) {
            specs = specs.and(TodoSpecifications.ancestorGroupPaidFor());
        }

        log.info("How many todos would have reminders? : {}", todoRepository.count(specs));
        log.info("And just on action date before? : {}", todoRepository.count(TodoSpecifications.actionByDateAfter(Instant.now())));
        List<Todo> todos = new ArrayList<>(); // until confident (given costs of last time)

        log.info("Sending scheduled reminders for {} todos, after using threshold of {}", todos.size(), COMPLETION_PERCENTAGE_BOUNDARY);
        todos.forEach(todo -> todoBroker.sendScheduledReminder(todo.getUid()));
    }

    @Override
    public void onApplicationEvent(AlterConfigVariableEvent event) {
        if (!StringUtils.isEmpty(event.getKey()) && configVariables.containsKey(event.getKey())) {
            log.info("Config variable relevant to scheduled tasks changed, updating");
            configRepository.findOneByKey(event.getKey())
                    .ifPresent(variable -> configVariables.put(variable.getKey(), variable.getValue()));
        }
    }
}