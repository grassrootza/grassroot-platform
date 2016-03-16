package za.org.grassroot.services.job;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;
import za.org.grassroot.services.EventLogManagementService;

import javax.jms.Message;
import javax.jms.ObjectMessage;
import java.util.List;

/**
 * Created by aakilomar on 10/5/15.
 */

@Component
public class ScheduledTasks {

    private Logger log = LoggerFactory.getLogger(getClass().getCanonicalName());

    //@Value("${reminderminutes}")
    //private int reminderminutes;

    //private static final SimpleDateFormat dateFormat = new SimpleDateFormat("HH:mm:ss");

    @Autowired
    EventRepository eventRepository;

    @Autowired
    EventLogManagementService eventLogManagementService;

    @Autowired
    GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    LogBookRepository logBookRepository;

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        log.info("sendReminders...starting");
        try {
            List<Event> eventList = eventRepository.findEventsForReminders();
            if (eventList != null) {
                for (Event event : eventList) {
                    log.info("sendReminders...event..." + event.getId());
                    // queue reminder request
                    jmsTemplateProducerService.sendWithNoReply("event-reminder", new EventDTO(event));
                    // update event with noreminderssent = noremindersent + 1 so we dont send it again
                    event.setNoRemindersSent(event.getNoRemindersSent() + 1);
                    event = eventRepository.save(event);

                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("sendReminders...done");

    }

    @Scheduled(fixedRate = 60000) //runs every 1 minutes
    public void sendVoteResults() {
        log.info("sendVoteResults...starting");
        try {
            List<Event> eventList = eventRepository.findUnsentVoteResults();
            if (eventList != null) {
                for (Event event : eventList) {
                    log.info("sendVoteResults...vote..." + event.getId());
                    // get the totals
                    RSVPTotalsDTO rsvpTotalsDTO = eventLogManagementService.getVoteResultsForEvent(event);

                    // queue vote results request
                    jmsTemplateProducerService.sendWithNoReply("vote-results", new EventWithTotals(new EventDTO(event),rsvpTotalsDTO));
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("sendVoteResults...done");

    }

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendLogBookReminders() {
        log.info("sendLogBookReminders...starting");
        int count = 0;
        try {

            List<LogBook> logBookList = logBookRepository.findLogBookReminders();
            // queue to logbook-reminders

            for (LogBook l : logBookList) {
                jmsTemplateProducerService.sendWithNoReply("logbook-reminders", new LogBookDTO(l));
                count++;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("sendLogBookReminders..." + count + "...queued to logbook-reminders");

    }

    /*
    The reason for the bit of queue indirection is to create a bit of a delay, after the user setup is complete
    and before we start sending the welcome messages
     */
    @Scheduled(fixedRate = 900000) //runs every 15 minutes
    public void queueWelcomeMessages() {
        log.info("queueWelcomeMessages...starting");
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
        log.info("queueWelcomeMessages..." + count + "...queued to generic-async");

    }
}
