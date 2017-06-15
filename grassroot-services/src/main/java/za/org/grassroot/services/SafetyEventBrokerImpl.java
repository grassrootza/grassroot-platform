package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.SafetyEventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.user.AddressBroker;
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

    private static final Logger log = LoggerFactory.getLogger(SafetyEventBrokerImpl.class);

    private static final long FALSE_ALARM_THRESHOLD = 3;

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private MessagingServiceBroker messagingServiceBroker;

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

        log.info("Sending out safety alert messages, to {} members", group.getMembers().size());
        //need to send out smses immediately
        group.getMembers().stream()
                .filter(m -> !requestor.equals(m))
                .forEach(m -> sendSafetyNotice(safetyEvent, requestor, address, m));

        return safetyEvent;
    }

    private void sendSafetyNotice(SafetyEvent safetyEvent, User requestor, Address address, User respondent) {
        cacheUtilService.putSafetyEventResponseForUser(respondent, safetyEvent);
        String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address, false);
        messagingServiceBroker.sendPrioritySMS(message, respondent.getPhoneNumber());
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
        User respondent = userRepository.findOneByUid(userUid);

        safetyEvent.setRespondedTo(true);
        safetyEvent.setActive(false);
        safetyEvent.setFalseAlarm(!isValid);

        if (!isValid) {
            User requestor = safetyEvent.getActivatedBy();
            long count = safetyEventRepository.countByActivatedByAndCreatedDateTimeAfterAndFalseAlarm(requestor, Instant.now().minus(30, ChronoUnit.DAYS), true);
            String message = (count++ > 3) ?
                    messageAssemblingService.createBarringMessage(safetyEvent.getActivatedBy()) :
                    messageAssemblingService.createFalseSafetyEventActivationMessage(safetyEvent.getActivatedBy(), count);
            messagingServiceBroker.sendSMS(requestor.getPhoneNumber(), message, false);
        }

        Group group = safetyEvent.getGroup();
        User requestor = safetyEvent.getActivatedBy();
        group.getMembers().stream()
                .filter(m -> !requestor.equals(m))
                .forEach((m -> sendRespondedNotice(safetyEvent, respondent, m)));

    }

    private void sendRespondedNotice(SafetyEvent safetyEvent, User responder, User member) {
        messagingServiceBroker.sendSMS(messageAssemblingService.createSafetyEventReportMessage(
                member, responder, safetyEvent, true), member.getPhoneNumber(), false);
        cacheUtilService.clearSafetyEventResponseForUser(member, safetyEvent);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid) {
        Objects.nonNull(userUid);
        User user = userRepository.findOneByUid(userUid);
        return cacheUtilService.getOutstandingSafetyEventResponseForUser(user);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean needsToRespondToSafetyEvent(User sessionUser) {
        return getOutstandingUserSafetyEventsResponse(sessionUser.getUid()) != null;
    }

    @Override
    @Transactional
    public void setSafetyGroup(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!group.getCreatedByUser().equals(user)) {
            throw new AccessDeniedException("Error! Safety group must be created by the user");
        }

        user.setSafetyGroup(group);
    }

    @Override
    @Transactional
    public void resetSafetyGroup(String userUid, boolean deactivateGroup) {
        User user = userRepository.findOneByUid(userUid);
        final String priorSafetyGroupUid = user.getSafetyGroup().getUid();
        user.setSafetyGroup(null);
        Group group = groupRepository.findOneByUid(priorSafetyGroupUid); // since we may need to force some eager fetching
        // as usual, deactivating a group is surrounded by safeguards, so, here, must make sure that (a) group has never
        // done anything except be a safety group, and (b) the calling user is the creator (should also be enforced by UI)
        if (deactivateGroup && group.getCreatedByUser().equals(user)
                && group.getDescendantEvents().isEmpty() && group.getDescendantEvents().isEmpty()) {
            group.setActive(false);
            groupLogRepository.save(new GroupLog(group, user, GroupLogType.GROUP_REMOVED, 0L, "safety group deactivated"));
        }
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
                messagingServiceBroker.sendSMS(message, respondent.getPhoneNumber(), false);
            }
        }

    }

}
