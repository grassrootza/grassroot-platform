package za.org.grassroot.webapp.enums;

import za.org.grassroot.core.domain.JpaEntityType;

/**
 * Created by luke on 2015/10/06.
 */
public enum USSDResponseTypes {

    MTG_RSVP,
    VOTE,
    RESPOND_SAFETY,
    RESPOND_TODO,
    RENAME_SELF,
    NONE;

    public static USSDResponseTypes fromJpaEntityType(JpaEntityType entityType) {
        if (entityType == null) {
            return NONE;
        } else {
            switch (entityType) {
                case GROUP:
                    return NONE;
                case MEETING:
                    return MTG_RSVP;
                case VOTE:
                    return VOTE;
                case TODO:
                    return RESPOND_TODO;
                case SAFETY:
                    return RESPOND_SAFETY;
                default:
                    return NONE;
            }
        }
    }

}
