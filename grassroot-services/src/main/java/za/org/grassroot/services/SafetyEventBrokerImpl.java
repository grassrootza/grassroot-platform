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
import za.org.grassroot.core.enums.SafetyEventLogType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.SafetyEventRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.services.SmsSendingService;

import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/07/18.
 */
@Service
public class SafetyEventBrokerImpl implements SafetyEventBroker {


    private Logger log = LoggerFactory.getLogger(SafetyEventBrokerImpl.class);

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private SafetyEventLogBroker safetyEventLogBroker;

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


    @Override
    @Transactional
    public SafetyEvent create(String userUid, String groupUid) {

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User requestor = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        SafetyEvent safetyEvent = safetyEventRepository.save(new SafetyEvent(requestor, group));
        log.info("Activated a safety event with id {}", safetyEvent.getId());
        safetyEventLogBroker.create(userUid, safetyEvent.getUid(), SafetyEventLogType.ACTIVATED, false, null);
        Address address = addressBroker.getUserAddress(requestor.getUid());



        //need to send out smses immediately
        for (User respondent : group.getMembers()) {
            if(!respondent.equals(requestor)){
            String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address, false);
            smsSendingService.sendSMS(message, respondent.getPhoneNumber());
        }}


        return safetyEvent;
    }

    @Override
    @Transactional
    public SafetyEvent load(String safetyEventUid) {
        return safetyEventRepository.findOneByUid(safetyEventUid);
    }

    @Override
    @Transactional
    public void deactivate(String uid){
        SafetyEvent safetyEvent = safetyEventRepository.findOneByUid(uid);
        if(safetyEvent.isActive()) safetyEvent.setActive(false);
        safetyEventRepository.save(safetyEvent);

    }


    @Override
    @Transactional
    public List<SafetyEvent> fetchGroupSafetyEvents(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return safetyEventRepository.findByParentGroup(group);
    }

    @Override
    @Transactional
    public List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid) {

        List<SafetyEvent> safetyEvents = new ArrayList<>();
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(user.getSafetyGroupUid());

        List<SafetyEvent> fetchGroupSafetyEvents = fetchGroupSafetyEvents(group.getUid());
        for (SafetyEvent safetyEvent : fetchGroupSafetyEvents)
            if (!safetyEvent.getActivatedBy().equals(user) && !safetyEventLogBroker.hasRecordedResponse(userUid, safetyEvent.getUid())) {
                safetyEvents.add(safetyEvent);
            }
        return safetyEvents;

    }



    @Override
    @Transactional
    public void sendReminders(String uid){

        SafetyEvent safetyEvent = safetyEventRepository.findOneByUid(uid);

        if(!safetyEvent.isActive()){
            throw new IllegalStateException("Safety event is inactive");
        }
        Group group = safetyEvent.getParentGroup();
        User requestor = safetyEvent.getActivatedBy();
        Address address = addressBroker.getUserAddress(requestor.getUid());
        safetyEvent.updateScheduledReminderTime();

        if(safetyEvent.getScheduledReminderTime().isAfter(safetyEvent.getCreatedDateTime().plus(1, ChronoUnit.HOURS))){
            safetyEvent.setActive(false);
        }
        safetyEventRepository.save(safetyEvent);

        for (User respondent : group.getMembers()) {
            if(!respondent.equals(requestor)){
            String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address, true);
            smsSendingService.sendSMS(message, respondent.getPhoneNumber());
        }}

    }

}
