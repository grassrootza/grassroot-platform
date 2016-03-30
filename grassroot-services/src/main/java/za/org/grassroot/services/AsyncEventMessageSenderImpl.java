package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.Vote;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.EventRepository;
import za.org.grassroot.core.repository.MeetingRepository;
import za.org.grassroot.core.repository.VoteRepository;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

@Service
public class AsyncEventMessageSenderImpl implements AsyncEventMessageSender {
    private final Logger logger = LoggerFactory.getLogger(AsyncEventMessageSenderImpl.class);

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

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

    @Override
    public void sendNewVoteNotifications(String voteUid) {
        Vote vote = voteRepository.findOneByUid(voteUid);
        logger.info("Loadded the vote ... has this UID ... " + vote.getUid());
        jmsTemplateProducerService.sendWithNoReply("event-added", new EventDTO(vote));
        logger.info("Queued to event-added..." + vote.getUid() + "...version..." + vote.getVersion());
    }

    @Override
    @Async
    public void sendCancelMeetingNotifications(String meetingUid) {
        Meeting meeting = meetingRepository.findOneByUid(meetingUid);
        logger.info("About to cancel the meeting ..." + meeting.getUid());
        jmsTemplateProducerService.sendWithNoReply("event-cancelled", new EventDTO(meeting));
        logger.info("Queued to event-cancelled..." + meeting.getId() + "...version..." + meeting.getVersion());
    }

    @Override
    @Async
    public void sendChangedEventNotification(String eventUid, EventType eventType, boolean startTimeChanged) {
        EventChanged eventChanged;
        if (eventType == EventType.MEETING)
            eventChanged = new EventChanged(new EventDTO(meetingRepository.findOneByUid(eventUid)), startTimeChanged);
        else
            eventChanged = new EventChanged(new EventDTO(eventRepository.findOneByUid(eventUid)), startTimeChanged);
        jmsTemplateProducerService.sendWithNoReply("event-changed", eventChanged);
        logger.info("Queued to event-changed event..." + eventUid + "...startTimeChanged..." + startTimeChanged);
    }

}
