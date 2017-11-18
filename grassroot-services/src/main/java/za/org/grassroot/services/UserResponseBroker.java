package za.org.grassroot.services;

import za.org.grassroot.core.domain.EntityForUserResponse;
import za.org.grassroot.core.domain.JpaEntityType;

public interface UserResponseBroker {

    /**
     * Check if there is an entity that the user needs to respond to right now
     * @param userUid The UID of the user
     * @param checkSafetyAlerts Whether to also check safety alerts (might be false depending on client)
     * @return The most likely entity, if it exists, or null if there is nothing to respond to
     */
    EntityForUserResponse checkForEntityForUserResponse(String userUid, boolean checkSafetyAlerts);

    /**
     * Check whether the user has something to respond to, based on an incoming message
     * @param userUid
     * @param response
     * @param checkAlerts
     * @return
     */
    EntityForUserResponse checkForPossibleEntityResponding(String userUid, String response, boolean checkAlerts);

    /**
     * Records user response, dispatching to whichever broker is necessary
     * @param userUid User responding
     * @param entityType Type of the entity responding to
     * @param entityUid The UID of the entity
     * @param userResponse The user response (note special case: for safety alerts, add "false_alarm" for that)
     */
    void recordUserResponse(String userUid, JpaEntityType entityType, String entityUid, String userResponse);

}
