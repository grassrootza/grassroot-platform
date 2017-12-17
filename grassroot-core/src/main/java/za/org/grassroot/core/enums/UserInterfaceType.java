package za.org.grassroot.core.enums;


public enum UserInterfaceType {
    UNKNOWN("unknown"),
    USSD("ussd"),
    WEB("web"),
    ANDROID("android"),
    SYSTEM("system"),
    INCOMING_SMS("incoming sms");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
