package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.EventChanged;
import za.org.grassroot.core.dto.EventDTO;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AsyncEventMessageSenderImpl implements AsyncEventMessageSender {
    private final Logger logger = LoggerFactory.getLogger(AsyncEventMessageSenderImpl.class);

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private MeetingRepository meetingRepository;

    @Autowired
    private VoteRepository voteRepository;

    @Autowired
    private AccountLogRepository accountLogRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private GenericJmsTemplateProducerService jmsTemplateProducerService;

    @Override
    @Async
    public void sendNewMeetingNotifications(String meetingUid) {
        Meeting meeting = meetingRepository.findOneByUid(meetingUid);
        logger.info("Loaded the meeting ... has this ID ... " + meeting.getId());
        jmsTemplateProducerService.sendWithNoReply("event-added", meetingUid);
        logger.info("Queued to event-added..." + meeting.getId() + "...version..." + meeting.getVersion());
    }

    @Override
    public void sendNewVoteNotifications(String voteUid) {
        Vote vote = voteRepository.findOneByUid(voteUid);
        logger.info("Loadded the vote ... has this UID ... " + vote.getUid());
        jmsTemplateProducerService.sendWithNoReply("event-added", voteUid);
        logger.info("Queued to event-added..." + vote.getUid() + "...version..." + vote.getVersion());
    }

    @Override
    @Async
    public void sendCancelMeetingNotifications(String meetingUid) {
        Meeting meeting = meetingRepository.findOneByUid(meetingUid);
        logger.info("About to cancel the meeting ..." + meeting.getUid());
        jmsTemplateProducerService.sendWithNoReply("event-cancelled", meetingUid);
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

    @Override
    @Transactional
    @Async
    public void sendFreeFormMessage(String sendingUserUid, String groupUid, String message) {
        // todo: move most of this to AccountManager
        // for now, just let the notification consumer handle the group loading etc., here just check the user
        // has permission (is account admin--later, account admin and it's a paid group, with enough credit

        User user = userRepository.findOneByUid(sendingUserUid);
        Account account = user.getAccountAdministered();

        Set<String> standardRoleNames = user.getStandardRoles().stream().map(s -> s.getName()).collect(Collectors.toSet());
        logger.info("User's standard roles: " + standardRoleNames);
        if (account == null || !standardRoleNames.contains(BaseRoles.ROLE_ACCOUNT_ADMIN)) {
            throw new AccessDeniedException("User not account admin!");
        }
        Map<String, String> messageMap = new HashMap<>();
        messageMap.put("group-uid", groupUid);
        messageMap.put("message", message);
        jmsTemplateProducerService.sendWithNoReply("free-form", messageMap);
        logger.info("Queued to free-form... for group" + groupUid + ", with message: " + message);
        accountLogRepository.save(new AccountLog(sendingUserUid, account, AccountLogType.MESSAGE_SENT, groupUid, null,
                                                 "Sent free form message"));
    }

}
