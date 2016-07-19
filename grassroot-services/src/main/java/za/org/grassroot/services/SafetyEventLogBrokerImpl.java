package za.org.grassroot.services;

import org.springframework.beans.factory.annotation.Autowired;
import za.org.grassroot.core.domain.SafetyEvent;
import za.org.grassroot.core.domain.SafetyEventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.SafetyEventLogType;
import za.org.grassroot.core.repository.SafetyEventLogRepository;

import java.util.Objects;

/**
 * Created by paballo on 2016/07/19.
 */
public class SafetyEventLogBrokerImpl implements SafetyEventLogBroker {

    @Autowired
    private SafetyEventBroker safetyEventBroker;

    @Autowired
    private SafetyEventLogRepository safetyEventLogRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Override
    public SafetyEventLog create(String userUid, String safetyEventUid, SafetyEventLogType safetyEventLogType, boolean response, String validity) {
        Objects.nonNull(userUid);
        Objects.nonNull(safetyEventUid);

        User user = userManagementService.load(userUid);
        SafetyEvent safetyEvent = safetyEventBroker.load(safetyEventUid);
        SafetyEventLog safetyEventLog = new SafetyEventLog(user, safetyEvent, safetyEventLogType, response, validity);

        safetyEventLogRepository.save(safetyEventLog);

        return safetyEventLog;
    }

    @Override
    public void recordResponse(String userUid, String eventUid, SafetyEventLogType safetyEventLogType, boolean response) {
        Objects.nonNull(userUid);
        Objects.nonNull(eventUid);

        create(userUid, eventUid, safetyEventLogType, response, null);
    }

    @Override
    public void recordValidity(String userUid, String safetyEventUid, String validity) {
        Objects.nonNull(userUid);
        Objects.nonNull(safetyEventUid);

        User user = userManagementService.load(userUid);
        SafetyEvent safetyEvent = safetyEventBroker.load(safetyEventUid);

        SafetyEventLog safetyEventLog = safetyEventLogRepository.findByUserAndSafetyEvent(user, safetyEvent);
        safetyEventLog.setValidity(validity);

        safetyEventLogRepository.save(safetyEventLog);
    }



    @Override
    public boolean userRecordedResponse(String userUid, String safetyEventUid) {
        Objects.nonNull(userUid);
        Objects.nonNull(safetyEventUid);
        User user = userManagementService.load(userUid);
        SafetyEvent safetyEvent = safetyEventBroker.load(safetyEventUid);

        return safetyEventLogRepository.findByUserAndSafetyEvent(user,safetyEvent) !=null;
    }


}
