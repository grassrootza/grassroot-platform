package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/15.
 */
public enum UserInterfaceType {
    UNKNOWN("unknown"),
    USSD("ussd"),
    WEB("web"),
    ANDROID("android");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }

    public static UserInterfaceType fromString(String template) {

        if (template != null) {
            for (UserInterfaceType t : UserInterfaceType.values()) {
                if (template.equalsIgnoreCase(t.text)) {
                    return t;
                }
            }
        }
        return UserInterfaceType.UNKNOWN;
    }
}
