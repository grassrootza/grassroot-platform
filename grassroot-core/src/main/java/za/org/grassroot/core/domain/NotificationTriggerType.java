package za.org.grassroot.core.domain;

/*
Defines the types of actions that can trigger an outbound notification. Can and will add others after
 */
public enum NotificationTriggerType {

    ADDED_TO_GROUP, // i.e., was added by organizer or via join code
    CAMPAIGN_RESPONSE, // i.e., triggered a campaign message
    SIGNED_PETITION // i.e., signed a petition

}
