package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.SafetyEventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.services.SmsSendingService;
import za.org.grassroot.services.util.CacheUtilService;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/07/18.
 */
@Service
public class SafetyEventBrokerImpl implements SafetyEventBroker {


    private Logger log = LoggerFactory.getLogger(SafetyEventBrokerImpl.class);

    private static final long FALSE_ALARM_THRESHOLD = 3;

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private SmsSendingService smsSendingService;

    @Autowired
    private AddressBroker addressBroker;

    @Autowired
    private CacheUtilService cacheUtilService;


    @Override
    @Transactional
    public SafetyEvent create(String userUid, String groupUid) {

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User requestor = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        SafetyEvent safetyEvent = safetyEventRepository.save(new SafetyEvent(requestor, group));
        Address address = addressBroker.getUserAddress(requestor.getUid());

        //need to send out smses immediately
        for (User respondent : group.getMembers()) {
            if (!respondent.equals(requestor)) {
                cacheUtilService.putSafetyEventResponseForUser(respondent, safetyEvent);
                String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address, false);
                smsSendingService.sendSMS(message, respondent.getPhoneNumber());
            }
        }

        return safetyEvent;
    }

    @Override
    @Transactional(readOnly = true)
    public SafetyEvent load(String safetyEventUid) {
        return safetyEventRepository.findOneByUid(safetyEventUid);
    }


    @Override
    @Transactional
    public void recordResponse(String userUid, String safetyEventUid, boolean isValid) {
        Objects.nonNull(userUid);
        Objects.nonNull(safetyEventUid);
        SafetyEvent safetyEvent = safetyEventRepository.findOneByUid(safetyEventUid);

        if (!isValid) {
            User requestor = safetyEvent.getActivatedBy();
            long count = safetyEventRepository.countByActivatedByAndCreatedDateTimeAfterAndFalseAlarm(requestor, Instant.now().minus(30, ChronoUnit.DAYS), true);
            String message;
            if (count++ > 3) {
                message = messageAssemblingService.createBarringMessage(safetyEvent.getActivatedBy());
            } else {
                message = messageAssemblingService.createFalseSafetyEventActivationMessage(safetyEvent.getActivatedBy(), count);
            }
            smsSendingService.sendSMS(message, requestor.getPhoneNumber());
        }

        Group group = safetyEvent.getGroup();
        User requestor = safetyEvent.getActivatedBy();
        for (User respondent : group.getMembers()) {
            if (!respondent.equals(requestor)) {
                cacheUtilService.clearSafetyEventResponseForUser(respondent, safetyEvent);
            }
        }

        safetyEvent.setRespondedTo(true);
        safetyEvent.setActive(false);
        safetyEvent.setFalseAlarm(!isValid);

    }

    @Override
    @Transactional
    public List<SafetyEvent> fetchGroupSafetyEvents(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return safetyEventRepository.findByGroup(group);
    }

    @Override
    @Transactional
    public List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid) {
        Objects.nonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return cacheUtilService.getOutstandingSafetyEventResponseForUser(user);

    }

    @Override
    @Transactional(readOnly = true)
    public boolean isUserBarred(String userUid) {

        User user = userRepository.findOneByUid(userUid);
        Instant from = Instant.now().minus(30, ChronoUnit.DAYS);
        long falseAlarmCount = safetyEventRepository.countByActivatedByAndCreatedDateTimeAfterAndFalseAlarm(user, from, true);
        return falseAlarmCount > FALSE_ALARM_THRESHOLD;
    }


    @Override
    @Transactional
    public void sendReminders(String uid) {

        SafetyEvent safetyEvent = safetyEventRepository.findOneByUid(uid);

        if (!safetyEvent.isActive()) {
            throw new IllegalStateException("Safety event is inactive");
        }
        Group group = safetyEvent.getGroup();
        User requestor = safetyEvent.getActivatedBy();
        Address address = addressBroker.getUserAddress(requestor.getUid());
        safetyEvent.updateScheduledReminderTime();

        if (safetyEvent.getScheduledReminderTime().isAfter(safetyEvent.getCreatedDateTime().plus(1, ChronoUnit.HOURS))) {
            safetyEvent.setActive(false);
        }

        for (User respondent : group.getMembers()) {
            if (!respondent.equals(requestor)) {
                String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address, true);
                smsSendingService.sendSMS(message, respondent.getPhoneNumber());
            }
        }

    }

}
