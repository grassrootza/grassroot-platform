package za.org.grassroot.core.domain;

/*
Defines the types of actions that can trigger an outbound notification. Can and will add others after
 */
public enum BroadcastType {

    ADDED_TO_GROUP, // i.e., sent to user when they are added to the group organizer or via join code
    FUTURE,
    IMMEDIATE

}
