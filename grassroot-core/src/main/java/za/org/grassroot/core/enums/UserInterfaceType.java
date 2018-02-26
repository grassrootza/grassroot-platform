package za.org.grassroot.core.enums;


public enum UserInterfaceType {
    UNKNOWN("unknown"),
    SYSTEM("system"),

    USSD("ussd"),
    WEB("web"),
    ANDROID("android"),
    INCOMING_SMS("incoming sms"),
    WEB_2("rebuilt_frontend"),
    ANDROID_2("second android"),

    FACEBOOK("facebook"),
    TWITTER("twitter"),

    EMAIL("incoming_email"),
    WHATSAPP("incoming_whatsapp");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
