package za.org.grassroot.services.async;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.LogBook;
import za.org.grassroot.core.dto.*;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.integration.services.NotificationService;

import za.org.grassroot.services.EventBroker;
import za.org.grassroot.services.async.GenericJmsTemplateProducerService;

import javax.jms.Message;
import javax.jms.ObjectMessage;
import java.util.List;

/**
 * Created by aakilomar on 10/5/15.
 */

@Component
public class ScheduledTasks {

    private Logger log = LoggerFactory.getLogger(getClass().getCanonicalName());

    @Autowired
    private EventBroker eventBroker;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Autowired
    private LogBookRepository logBookRepository;

    @Autowired
    NotificationService notificationService;

    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void sendReminders() {
        eventBroker.sendScheduledReminders();
    }

    @Scheduled(fixedRate = 60000) //runs every 1 minutes
    public void sendVoteResults() {
        eventBroker.sendVoteResults();
    }

    @Scheduled(cron = "0 0 16 * * *") // runs at 4pm (=6pm SAST) every day
    public void sendMeetingAcknowledgements() { eventBroker.sendMeetingAcknowledgements(); }

    @Scheduled(fixedRate = 3600000) // runs every hour
    public void sendMeetingRSVPsToDate() { eventBroker.sendMeetingRSVPsToDate(); }

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


    @Scheduled(fixedRate = 300000) //runs every 5 minutes
    public void resendFailedDeliveries() {
        log.info("sending messages via sms that were not delivered by gcm");
        notificationService.resendNotDelivered();
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
