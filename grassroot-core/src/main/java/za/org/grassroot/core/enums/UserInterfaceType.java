package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/15.
 */
public enum UserInterfaceType {
    UNKNOWN("unknown"),
    USSD("ussd"),
    WEB("web"),
    ANDROID("android"),
    SYSTEM("system");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
