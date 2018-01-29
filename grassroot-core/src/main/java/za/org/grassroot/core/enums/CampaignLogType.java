package za.org.grassroot.core.enums;


public enum CampaignLogType {

    CREATED_IN_DB("campaign created"),
    CAMPAIGN_MESSAGES_SET("campaign messages set"),
    CAMPAIGN_MESSAGE_ADDED("campaign message added"),
    CAMPAIGN_TAG_ADDED("campaign tag added"),
    CAMPAIGN_MESSAGE_ACTION_ADDED("campaign message action added"),
    CAMPAIGN_LINKED_GROUP("campaign linked to master group"),
    CAMPAIGN_NOT_FOUND("campaign not found"),
    CAMPAIGN_FOUND("campaign found"),
    CAMPAIGN_MESSAGE_FOUND("campaign message found"),
    CAMPAIGN_MESSAGE_NOT_FOUND("campaign message not found"),
    CAMPAIGN_USER_ADDED_TO_MASTER_GROUP("campaign user added to master group"),
    CAMPAIGN_BROADCAST_SENT("send a broadcast to campaign");

    private final String text;

    CampaignLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
