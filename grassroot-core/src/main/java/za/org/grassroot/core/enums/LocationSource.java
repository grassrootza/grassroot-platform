package za.org.grassroot.core.enums;

/**
 * Created by luke on 2017/04/11.
 */
public enum LocationSource {

    CALCULATED, // i.e., an average across logs of members/events/logs/etc
    LOGGED, // i.e., direct input
    LOG_AVERAGE // i.e., best possible, average of direct logged GPSs

}