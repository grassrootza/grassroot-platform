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
    CHANGED_LANGUAGE("user changed their language");

    private final String text;

    private UserLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
