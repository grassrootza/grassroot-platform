package za.org.grassroot.scheduling;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.Todo;
import za.org.grassroot.core.domain.task.Todo_;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.TodoSpecifications;
import za.org.grassroot.services.SafetyEventBroker;
import za.org.grassroot.services.task.EventBroker;
import za.org.grassroot.services.task.TodoBroker;
import za.org.grassroot.services.task.VoteBroker;

import javax.persistence.criteria.Join;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 10/5/15.
 */
@Component
public class ScheduledTasks {

    private Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    @Value("${grassroot.todos.completion.threshold:20}") // defaults to 20 percent
    private double COMPLETION_PERCENTAGE_BOUNDARY;

    @Value("${grassroot.todos.reminders.send:false}")
    private boolean sendTodoReminders;

    @Value("${grassroot.events.reminders.unpaid.send:false}")
    private boolean sendUnpaidEventReminders;

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

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        List<Event> events = eventRepository.findEventsForReminders(Instant.now())
                .stream().filter(event -> sendUnpaidEventReminders || event.getAncestorGroup().isPaidFor()).collect(Collectors.toList());

        logger.debug("Sending scheduled reminders for {} event(s)", events.size());

        for (Event event : events) {
            try {
                eventBroker.sendScheduledReminder(event.getUid());
            } catch (Exception e) {
                logger.error("Error while sending scheduled reminder of event " + event + ": " + e.getMessage(), e);
            }
        }

        logger.info("Sending scheduled reminders...done");
    }

    @Scheduled(fixedRate = 300000)
    public void sendSafetyReminders() {
        List<SafetyEvent> safetyEvents = safetyEventRepository.findSafetyEvents(Instant.now().minus(1, ChronoUnit.HOURS), Instant.now());
        logger.info("Sending out safety reminders");
        if (safetyEvents != null) {
            safetyEvents.forEach(e -> safetyEventBroker.sendReminders(e.getUid()));
        }
    }

    @Scheduled(fixedRate = 60000) //runs every 1 minute
    public void sendUnsentVoteResults() {
        logger.debug("Checking for unsent votes ...");
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

        List<Event> meetings = meetingRepository.findAll(mtgCharacteristics.and(ancestorGroupPaidFor));
        logger.info("Sending out meeting thank you for {} meetings", meetings.size());

        for (Event meeting : meetings) {
            try {
                eventBroker.sendMeetingAcknowledgements(meeting.getUid());
            } catch (Exception ex) {
                logger.error("Error while sending acknowledgments for meeting " + meeting + ": " + ex.getMessage(), ex);
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

		logger.info("Sending out RSVP totals for {} meetings", meetings.size());
        for (Meeting meeting : meetings) {
            try {
                eventBroker.sendMeetingRSVPsToDate(meeting.getUid());
            } catch (Exception e) {
                logger.error("Error while sending responses for meeting " + meeting + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendTodoReminders() {
        if (sendTodoReminders) {
            List<Todo> todos = todoRepository.findAll(Specification.where(TodoSpecifications.notCancelled())
                    .and(TodoSpecifications.remindersLeftToSend())
                    .and(TodoSpecifications.reminderTimeBefore(Instant.now()))
                    .and((root, query, cb) -> cb.isFalse(root.get(Todo_.completed)))
                    .and(TodoSpecifications.todoNotConfirmedByCreator()));

            logger.info("Sending scheduled reminders for {} todos, after using threshold of {}", todos.size(), COMPLETION_PERCENTAGE_BOUNDARY);
            todos.forEach(todo -> todoBroker.sendScheduledReminder(todo.getUid()));
        }
    }

}