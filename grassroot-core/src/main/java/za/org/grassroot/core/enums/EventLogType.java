package za.org.grassroot.core.enums;

/**
 * Created by aakilomar on 8/26/15.
 */
/*
N.B. please always add new types at the end as this is an ordinal position based enum
and therefore adding new one in the middle will mess up your data
 */
public enum EventLogType {
    CREATED,
    REMINDER,
    CHANGE,
    CANCELLED,
    MINUTES,
    RSVP,
    TEST,
    RESULT,
    MANUAL_REMINDER,
    FREE_FORM_MESSAGE, // obsolete now, but keeping it to not mess with enum indices until its JPA storage is converted from default ORDINAL to STRING
    RSVP_TOTAL_MESSAGE,
    THANK_YOU_MESSAGE
}
