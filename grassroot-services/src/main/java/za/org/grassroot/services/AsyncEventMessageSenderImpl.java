package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

@Service
public class AsyncEventMessageSenderImpl implements AsyncEventMessageSender {
    private final Logger logger = LoggerFactory.getLogger(AsyncEventMessageSenderImpl.class);

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Override
    @Async
    public void sendNewMeetingNotifications(String meetingUid) {
        Meeting meeting = meetingRepository.findOneByUid(meetingUid);
        logger.info("Loaded the meeting ... has this ID ... " + meeting.getId());
        jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(meeting));
        logger.info("Queued to event-added..." + meeting.getId() + "...version..." + meeting.getVersion());
    }
}
