package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/02/22.
 */
public enum UserLogType {
    CREATED_IN_DB("user entity created"),
    INITIATED_USSD("user initiated first USSD session"),
    CREATED_WEB("user created a web profile"),
    REGISTERED_ANDROID("user registered on Android"),
    CHANGED_LANGUAGE("user changed their language"),
    USER_SESSION("user initiated a session"),
    USSD_MENU_ACCESSED("user accessed a USSD menu"),
    USSD_INTERRUPTED("user was interrupted on a USSD menu"),
    USSD_DATE_ENTERED("user entered a date time string in USSD"),
    USSD_DATE_WRONG("user corrected a date time string"),
    USER_SKIPPED_NAME("user preferred not to set name"),
    DEREGISTERED_ANDROID("user android profile deleted"),
    JOIN_REQUEST("user required to approve a join request"),
    JOIN_REQUEST_REMINDER("user reminded to response to join request"),
    JOIN_REQUEST_APPROVED("user was approved to join a group"),
    JOIN_REQUEST_DENIED("user was denied joining a group"),
    JOINED_SAFETY_GROUP("user added to safety group"),
    USED_A_JOIN_CODE("user joined a group via a join code"),
    ADDED_ADDRESS("user added address"),
    CHANGED_ADDRESS("user changed address"),
    REMOVED_ADDRESS("user removed addres"),
    USED_PROMOTIONAL_CODE("user entered using a promotional code"),
    GAVE_LOCATION_PERMISSION("user gave permission to track location"),
    REVOKED_LOCATION_PERMISSION("user revoked permission to track location"),
    ONCE_OFF_LBS_REVERSAL("LBS permission removed automatically after once off request"),
    LOCATION_PERMISSION_ENABLED("msisdn added to ussd location tracking service"),
    LOCATION_PERMISSION_REMOVED("msisdn removed from location tracking service"),
    LIVEWIRE_CONTACT_GRANTED("granted permission to be a LiveWire contact"),
    LIVEWIRE_CONTACT_REVOKED("revoked livewire permission");

    private final String text;

    UserLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
