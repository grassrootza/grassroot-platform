package za.org.grassroot.core.domain.broadcast;

public enum BroadcastSchedule {

    ADDED_TO_GROUP, // i.e., sent to user when they are added to the group organizer or via join code
    ENGAGED_CAMPAIGN, // i.e., user dialed into USSD code
    RETRIEVED_INFORMATION, // i.e., end of an Izwe Lami session (or, future, Acc Stack etc)
    FUTURE,
    IMMEDIATE

}
