package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/04/02.
 * Enum to help distinguish between whether a user prefers our-app notifcations (Android), or SMS, or -- later -- other
 * types of messages as their default. This may also be used for recording messages send and so forth.
 * New messaging options should be added at the bottom.
 */
public enum UserMessagingPreference {
    SMS,
    ANDROID_APP, // i.e., push notifications within the Grassroot App
    EMAIL, // though note we will have to work out how to put in place threshold limits for broadcasts if no MailChimp
    WEB_ONLY // for security/privacy-conscious users who do not want the SMSs and do not have Android
}
