package za.org.grassroot.core.enums;

/**
 * Created by luke on 2017/04/11.
 */
public enum LocationSource {

    CALCULATED, // i.e., an average across logs of members/events/logs/etc
    LOGGED_APPROX, // i.e., direct input from an unreliable / medium-accuracy source
    LOGGED_PRECISE, // i.e., direct input from a reliable source
    LOGGED_MULTIPLE, // i.e., best possible, average of direct logged GPS
    UNKNOWN; // just in case corrupted call etc

    public static LocationSource convertFromInterface(UserInterfaceType type) {
        switch (type) {
            case WEB:
                return LocationSource.LOGGED_PRECISE;
            case ANDROID:
                return LocationSource.LOGGED_PRECISE;
            case USSD:
                return LocationSource.LOGGED_APPROX;
            case UNKNOWN:
            default:
                return LocationSource.LOGGED_APPROX;
        }
    }

}