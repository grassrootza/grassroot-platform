package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/22.
 */
public enum AccountLogType {

    ACCOUNT_CREATED,
    ACCOUNT_ENABLED,
    ACCOUNT_SUB_ID_CHANGED,
    ACCOUNT_DISABLED,

    ADMIN_ADDED,
    ADMIN_REMOVED,
    EMAIL_CHANGED,
    GROUP_ADDED,
    GROUP_REMOVED,
    MESSAGE_SENT,
    COST_CALCULATED,
    BILL_CALCULATED,

    NAME_CHANGED,
    PAYMENT_CHANGED,
    PAYMENT_RECEIVED,
    PAYMENT_FAILED,
    LAST_BILLING_DATE_CHANGED,

    GROUP_WELCOME_MESSAGES_CREATED,
    GROUP_WELCOME_MESSAGES_CHANGED,
    GROUP_WELCOME_DEACTIVATED,
    GROUP_WELCOME_CONFLICT,
    GROUP_WELCOME_CASCADE_ON,
    GROUP_WELCOME_CASCADE_OFF,

    GEO_API_MESSAGE_SENT,

    CAMPAIGN_WELCOME_SENT,
    CAMPAIGN_WELCOME_ALTERED,

    BROADCAST_SCHEDULED,
    BROADCAST_MESSAGE_SENT;

}
