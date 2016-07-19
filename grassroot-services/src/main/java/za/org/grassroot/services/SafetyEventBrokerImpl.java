package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import za.org.grassroot.core.domain.Address;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.SafetyEventLogType;
import za.org.grassroot.core.repository.SafetyEventRepository;
import za.org.grassroot.integration.services.SmsSendingService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Created by paballo on 2016/07/18.
 */
public class SafetyEventBrokerImpl implements SafetyEventBroker {

    @Autowired
    private SafetyEventRepository safetyEventRepository;

    @Autowired
    private SafetyEventLogBroker safetyEventLogBroker;

    @Autowired
    private GroupBroker groupBroker;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private MessageAssemblingService messageAssemblingService;

    @Autowired
    private SmsSendingService smsSendingService;

    @Autowired
    private AddressBroker addressBroker;


    @Override
    public SafetyEvent create(String userUid, String groupUid) {

        Objects.nonNull(userUid);
        Objects.nonNull(groupUid);

        User requestor = userManagementService.load(userUid);
        Group group = groupBroker.load(groupUid);

        SafetyEvent safetyEvent = safetyEventRepository.save(new SafetyEvent(requestor, group));
        safetyEventLogBroker.create(userUid, safetyEvent.getUid(), SafetyEventLogType.ACTIVATED, false, null);
        Address address = addressBroker.getUserAddress(requestor.getUid());


        for (User respondent : group.getMembers()) {
            String message = messageAssemblingService.createSafetyEventMessage(respondent, requestor, address);
            smsSendingService.sendSMS(message, respondent.getPhoneNumber());
        }

        return safetyEvent;
    }

    @Override
    public SafetyEvent load(String safetyEventUid) {
        return null;
    }


    @Override
    public List<SafetyEvent> fetchGroupSafetyEvents(String groupUid) {
        Group group = groupBroker.load(groupUid);
        return safetyEventRepository.findByGroup(group);
    }

    @Override
    public List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid) {

        List<SafetyEvent> safetyEvents = new ArrayList<>();
        User user = userManagementService.load(userUid);
        Group group = groupBroker.load(user.getSafetyGroupUid());

        List<SafetyEvent> fetchGroupSafetyEvents = fetchGroupSafetyEvents(group.getUid());
        for (SafetyEvent safetyEvent : fetchGroupSafetyEvents)
            if (!safetyEvent.getActivatedBy().equals(user) && !safetyEventLogBroker.userRecordedResponse(userUid, safetyEvent.getUid())) {
                safetyEvents.add(safetyEvent);
            }
        return safetyEvents;

    }
}
