package za.org.grassroot.core.enums;

/**
 * Created by aakilomar on 8/26/15.
 */
/*
N.B. please always add new types at the end as this is an ordinal position based enum
and therefore adding new one in the middle will mess up your data
 */
public enum EventLogType {
    EventCreated,
    EventReminder,
    EventChange,
    EventCancelled,
    EventMinutes,
    EventRSVP,
    EventTest,
    EventResult,
    EventManualReminder,
    FreeFormMessage, // obsolete now, but keeping it to not mess with enum indices until its JPA storage is converted from default ORDINAL to STRING
    EventRsvpTotalMessage,
    EventThankYouMessage
}
