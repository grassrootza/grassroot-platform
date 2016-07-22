package za.org.grassroot.services;

import za.org.grassroot.core.domain.SafetyEventLog;
import za.org.grassroot.core.enums.SafetyEventLogType;

/**
 * Created by paballo on 2016/07/19.
 */
public interface SafetyEventLogBroker {

    SafetyEventLog create(String userUid, String safetyEventUid, SafetyEventLogType safetyEventLogType, boolean response, String validity);

    void recordResponse(String userUid, String eventUid, SafetyEventLogType safetyEventLogType, boolean response);

    void recordValidity(String userUid, String safetyEventUid, String validity);

    boolean hasRecordedResponse(String userUid, String safetyEventUid);





}
