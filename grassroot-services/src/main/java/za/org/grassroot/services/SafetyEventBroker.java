package za.org.grassroot.services;

import za.org.grassroot.core.domain.SafetyEvent;

import java.util.List;

/**
 * Created by paballo on 2016/07/18.
 */
public interface SafetyEventBroker {

    SafetyEvent create(String userUid, String groupUid);

    SafetyEvent load(String safetyEventUid);

    List<SafetyEvent> fetchGroupSafetyEvents(String groupUid);

    List<SafetyEvent> getOutstandingUserSafetyEventsResponse(String userUid);




}
