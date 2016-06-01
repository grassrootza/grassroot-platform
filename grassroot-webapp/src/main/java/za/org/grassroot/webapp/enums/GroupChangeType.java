package za.org.grassroot.webapp.enums;

import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.enums.EventType;

/**
 * Created by luke on 2016/06/01.
 */
public enum GroupChangeType {
    MEETING,
    VOTE,
    TODO,
    CREATED,
    MEMBER_ADDED,
    OTHER_CHANGE;

    public static GroupChangeType getChangeType(Event event) {
        if (event.getEventType().equals(EventType.MEETING))
            return GroupChangeType.MEETING;
        else if (event.getEventType().equals(EventType.VOTE))
            return GroupChangeType.VOTE;
        else
            throw new UnsupportedOperationException("Error! Should not have other event type passed here");
    }

    public static GroupChangeType getChangeType(GroupLog groupLog) {
        switch (groupLog.getGroupLogType()) {
            case GROUP_ADDED:
                return GroupChangeType.CREATED;
            case GROUP_MEMBER_ADDED:
                return GroupChangeType.MEMBER_ADDED;
            case GROUP_MEMBER_ADDED_VIA_JOIN_CODE:
                return GroupChangeType.MEMBER_ADDED;
            default:
                return OTHER_CHANGE;
        }
    }

}
