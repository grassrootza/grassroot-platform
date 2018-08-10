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
    REST_GENERIC("generic REST"),

    FACEBOOK("facebook"),
    TWITTER("twitter"),
    OTHER_WEB("other_web"), // so for other 3rd party joins etc

    EMAIL("incoming_email"),
    WHATSAPP("incoming_whatsapp"),
    PLEASE_CALL_ME("please_call_me");

    private final String text;

    UserInterfaceType(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }
}
