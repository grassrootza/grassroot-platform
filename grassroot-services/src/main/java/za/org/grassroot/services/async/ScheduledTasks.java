package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.GroupBroker;
import za.org.grassroot.services.LogBookBroker;
import za.org.grassroot.services.geo.GeoLocationBroker;

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
    private EventRepository eventRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    private GeoLocationBroker geoLocationBroker;

    @Autowired
    private GroupRepository groupRepository;

    // @Transactional
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

    // @Transactional
    @Scheduled(fixedRate = 60000) //runs every 1 minutes
    public void sendUnsentVoteResults() {
        List<Vote> votes = voteRepository.findUnsentVoteResults();
        if (!votes.isEmpty()) {
            logger.info("Sending vote results for {} unsent votes...", votes.size());
        }
        for (Vote vote : votes) {
            try {
                eventBroker.sendVoteResults(vote.getUid());
            } catch (Exception e) {
                logger.error("Error while sending vote results for vote " + vote + ": " + e.getMessage(), e);
            }
        }
    }

    // @Transactional
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

    // @Transactional
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

    // @Transactional
    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendLogBookReminders() {
        List<LogBook> logBooks = logBookRepository.findAllLogBooksForReminding();
        if (!logBooks.isEmpty()) {
            logger.info("Sending scheduled reminders for {} logbooks", logBooks.size());
        }

        for (LogBook logBook : logBooks) {
            try {
                logBookBroker.sendScheduledReminder(logBook.getUid());
            } catch (Throwable th) {
                logger.error("Error while sending reminder for logger book " + logBook + ": " + th.getMessage(), th);
            }
        }
    }

    // @Transactional
    @Scheduled(cron = "0 0 3 * * *") // runs at 3am every day
    public void calculateAggregateLocations() {
        // we had put few types of calculations here in sequence because one depends on
        // other being executed in order...

        LocalDate today = LocalDate.now();
        geoLocationBroker.calculatePreviousPeriodUserLocations(today);

        logger.info("Calculating group locations for date {}", today);
        List<Group> groups = groupRepository.findAll();
        for (Group group : groups) {
            // we don't want one big TX for all groups, so we separate each group location
            // calculation into its own transaction
            groupBroker.calculateGroupLocation(group.getUid(), today);
        }
    }

    // @Transactional
    @Scheduled(cron = "0 0 3 * * *") // runs at 1am every day
    public void removeInvalidGroups() {
        String invalidName = "1";
        Instant threshold = Instant.now().minus(7, ChronoUnit.DAYS);
        groupBroker.deleteInvalidGroups(invalidName,threshold);
    }

    // @Transactional
    @Scheduled(cron = "0 0 15 * * *") // runs at 3pm (= 5pm SAST) every day
    public void sendGroupJoinNotifications() { groupBroker.notifyOrganizersOfJoinCodeUse(Instant.now().minus(1, ChronoUnit.DAYS),
                                                                                         Instant.now());}
}
