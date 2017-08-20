package za.org.grassroot.webapp.enums;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.task.Event;
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

    private static final Logger logger = LoggerFactory.getLogger(GroupChangeType.class);

    public static GroupChangeType getChangeType(Event event) {
        if (event.getEventType().equals(EventType.MEETING))
            return GroupChangeType.MEETING;
        else if (event.getEventType().equals(EventType.VOTE))
            return GroupChangeType.VOTE;
        else
            throw new UnsupportedOperationException("Error! Should not have other event type passed here");
    }

    public static GroupChangeType getChangeType(GroupLog groupLog) {
        logger.debug("groupLog: {}", groupLog);
        if (groupLog == null) {
            return OTHER_CHANGE;
        } else {
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

}
