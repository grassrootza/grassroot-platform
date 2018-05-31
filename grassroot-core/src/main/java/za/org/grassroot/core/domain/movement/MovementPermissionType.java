package za.org.grassroot.core.domain.movement;

public enum MovementPermissionType {

    OPEN, // any movement member can add a group to it
    MODERATED, // only organizers can add groups
    CLOSED // only movement creator can add groups
}
