package za.org.grassroot.core.enums;


public enum UserInterfaceType {
    UNKNOWN("unknown"),
    USSD("ussd"),
    WEB("web"),
    ANDROID("android"),
    SYSTEM("system"),
    INCOMING_SMS("incoming sms"),
    WEB_2("rebuilt_frontend"),
    ANDROID_2("second android");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
