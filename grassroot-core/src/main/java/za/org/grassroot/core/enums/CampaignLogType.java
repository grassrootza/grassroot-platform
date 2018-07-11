package za.org.grassroot.core.enums;


public enum CampaignLogType {

    CREATED_IN_DB,
    CAMPAIGN_MESSAGES_SET,
    CAMPAIGN_MESSAGE_ADDED,
    CAMPAIGN_TAG_ADDED,
    CAMPAIGN_MESSAGE_ACTION_ADDED,
    CAMPAIGN_LINKED_GROUP,
    CAMPAIGN_NOT_FOUND,
    CAMPAIGN_MESSAGE_NOT_FOUND,
    CAMPAIGN_BROADCAST_SENT,
    CAMPAIGN_WELCOME_MESSAGE,

    CAMPAIGN_FOUND, // means engaged
    CAMPAIGN_EXITED_NEG,
    CAMPAIGN_PETITION_SIGNED,
    CAMPAIGN_USER_ADDED_TO_MASTER_GROUP,
    CAMPAIGN_SHARED,
    CAMPAIGN_USER_TAGGED,
    CAMPAIGN_REPLIED,

    CAMPAIGN_MODIFIED,
    CAMPAIGN_NAME_CHANGED,
    CAMPAIGN_DESC_CHANGED,
    CAMPAIGN_IMG_CHANGED,
    CAMPAIGN_IMG_REMOVED,
    CAMPAIGN_END_CHANGED,
    CAMPAIGN_URLS_CHANGED,
    CAMPAIGN_TYPE_CHANGED,
    CAMPAIGN_LANG_CHANGED,

    CAMPAIGN_DEACTIVATED,
    CAMPAIGN_REACTIVATED,
    SHARING_SETTINGS_ALTERED,
    WELCOME_MSG_ALTERED

}
