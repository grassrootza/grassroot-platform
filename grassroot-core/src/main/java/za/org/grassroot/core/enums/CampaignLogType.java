package za.org.grassroot.core.enums;


public enum CampaignLogType {

    CREATED_IN_DB("campaign created"),
    CAMPAIGN_MESSAGE_ADDED("campaign message added"),
    CAMPAIGN_TAG_ADDED("campaign tag added"),
    CAMPAIGN_MESSAGE_ACTION_ADDED("campaign message action added"),
    CAMPAIGN_LINKED_GROUP("campaign linked to master group");


    private final String text;

    CampaignLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
