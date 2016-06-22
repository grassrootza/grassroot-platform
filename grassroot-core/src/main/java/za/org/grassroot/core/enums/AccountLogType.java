package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/22.
 * NB: add new types at the bottom of the enum
 */
public enum AccountLogType {
    ACCOUNT_CREATED("account created"),
    ADMIN_CHANGED("account admin changed"),
    DETAILS_CHANGED("account details changed"),
    GROUP_ADDED("paid group added to account"),
    GROUP_REMOVED("paid group removed"),
    FEATURES_CHANGED("account features changed"),
    MESSAGE_SENT("free form message sent");

    private final String text;

    AccountLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
