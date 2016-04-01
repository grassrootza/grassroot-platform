package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/22.
 * NB: add new types at the bottom of the enum
 */
public enum UserLogType {
    CREATED_IN_DB("user entity created"),
    INITIATED_USSD("user initiated first USSD session"),
    CREATED_WEB("user created a web profile"),
    REGISTERED_ANDROID("user registered on Android"),
    CHANGED_LANGUAGE("user changed their language"),
    USER_USSD_SESSION("user initiated a USSD session"),
    USER_WEB_SESSION("user initiated a web session"),
    USER_ANDROID_SESSION("user logged in to Android"),
    USSD_PAGE_ACCESSED("user accessed a USSD menu"),
    USSD_INTERRUPTED("user was interrupted on a USSD menu"),
    USSD_DATE_ENTERED("user entered a date time string in USSD"),
    USSD_DATE_WRONG("user corrected a date time string"),
    USER_SKIPPED_NAME("user preferred not to set name");

    private final String text;

    private UserLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
