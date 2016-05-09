package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GenericAsyncDTO;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.integration.services.NotificationService;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;

import javax.jms.Message;
import javax.jms.ObjectMessage;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by aakilomar on 10/5/15.
 */

@Component
public class ScheduledTasks {

    private Logger logger = LoggerFactory.getLogger(ScheduledTasks.class);

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private LogBookBroker logBookBroker;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    private NotificationService notificationService;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        List<Event> events = eventRepository.findEventsForReminders(Instant.now());
        logger.info("Sending scheduled reminders for {} event(s)", events.size());

        for (Event event : events) {
            try {
                eventBroker.sendScheduledReminder(event.getUid());
            } catch (Exception e) {
                logger.error("Error while sending scheduled reminder of event " + event + ": " + e.getMessage(), e);
            }
        }

        logger.info("Sending scheduled reminders...done");
    }

    @Scheduled(fixedRate = 60000) //runs every 1 minutes
    public void sendUnsentVoteResults() {
        List<Vote> votes = voteRepository.findUnsentVoteResults();
        logger.info("Sending vote results for {} unsent votes...", votes.size());
        for (Vote vote : votes) {
            try {
                eventBroker.sendVoteResults(vote.getUid());
            } catch (Exception e) {
                logger.error("Error while sending vote results for vote " + vote + ": " + e.getMessage(), e);
            }
        }
    }

    @Scheduled(cron = "0 0 16 * * *") // runs at 4pm (=6pm SAST) every day
    public void sendMeetingThankYous() {
        LocalDate yesterday = LocalDate.now().minus(1, ChronoUnit.DAYS);
        Instant start = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MIN), getSAST());
        Instant end = convertToSystemTime(LocalDateTime.of(yesterday, LocalTime.MAX), getSAST());

        List<Meeting> meetings = meetingRepository.findByEventStartDateTimeBetweenAndCanceledFalseAndRsvpRequiredTrue(start, end);
        logger.info("Sending out meeting thank you for {} meetings", meetings.size());

        for (Meeting meeting : meetings) {
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
        List<Meeting> meetings = meetingRepository.meetingsForResponseTotals(Instant.now(), start, end);

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
    public void sendLogBookReminders() {
        List<LogBook> logBooks = logBookRepository.findAllLogBooksForReminding();
        logger.info("Sending scheduled reminders for {} logbooks", logBooks.size());

        for (LogBook logBook : logBooks) {
            try {
                logBookBroker.sendScheduledReminder(logBook.getUid());
            } catch (Throwable th) {
                logger.error("Error while sending reminder for logger book " + logBook + ": " + th.getMessage(), th);
            }
        }
    }


    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void resendFailedDeliveries() {
        logger.info("sending messages via sms that were not delivered by gcm");
        notificationService.resendNotDelivered();
    }

    /*
    The reason for the bit of queue indirection is to create a bit of a delay, after the user setup is complete
    and before we start sending the welcome messages
     */
    @Scheduled(fixedRate = 900000) //runs every 15 minutes
    public void queueWelcomeMessages() {
        logger.info("queueWelcomeMessages...starting");
        int count = 0;
        // wrap in try catch so that the scheduled thread does not die with any error
        try {
            // fetch all the messages from the "welcome-messages" queue and queue it for processing
            Message message;
            while ((message = jmsTemplateProducerService.receiveMessage("welcome-messages")) != null) {
                ObjectMessage objMessage = (ObjectMessage) message;
                jmsTemplateProducerService.sendWithNoReply("generic-async",new GenericAsyncDTO("welcome-messages",objMessage.getObject()));
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        logger.info("queueWelcomeMessages..." + count + "...queued to generic-async");
    }

//    @Scheduled(cron = "0 0 3 * * *") // runs at 3am every day
    public void calculatePreviousPeriodUserLocations() {
        LocalDate today = LocalDate.now();
        geoLocationBroker.calculatePreviousPeriodUserLocations(today);
    }

    @Scheduled(cron = "0 0 15 * * *") // runs at 3pm (= 5pm SAST) every day
    public void sendGroupJoinNotifications() { groupBroker.notifyOrganizersOfJoinCodeUse(Instant.now(),
                                                                                         Instant.now().minus(1, ChronoUnit.DAYS));}
}
