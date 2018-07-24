package za.org.grassroot.services.movement;

import za.org.grassroot.core.domain.movement.Movement;
import za.org.grassroot.core.domain.movement.MovementPermissionType;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public interface MovementBroker {

    Movement load(String movementUid);

    List<Movement> loadUserMovements(String userUid);

    String createMovement(String userUid, String name, MovementPermissionType permissionType);

    void alterPermissionType(String userUid, String movementUid, MovementPermissionType permissionType);

    void addOrganizer(String userUid, String movementUid, String organizerUid);

    void addMembers(String userUid, String movementUid, Collection<String> memberToAddUid);

    void addGroup(String userUid, String movementUid, String groupUid);

    void requestToJoin(String userUid, String movementUid);

    void requestToAddGroup(String userUid, String movementUid);

    void approveRequestToAddGroup(String userUid, String movementUid, String groupUid);

}
