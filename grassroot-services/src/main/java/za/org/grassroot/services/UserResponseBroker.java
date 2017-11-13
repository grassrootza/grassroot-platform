package za.org.grassroot.services;

import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.JpaEntityType;

public interface UserResponseBroker {

    /*
    Returns null if there is nothing
     */
    EntityForUserResponse checkForEntityForUserResponse(String userUid, boolean checkSafetyAlerts);

    void recordUserResponse(String userUid, JpaEntityType entityType, String entityUid, String userResponse);

}
