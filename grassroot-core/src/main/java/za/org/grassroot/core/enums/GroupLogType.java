package za.org.grassroot.core.enums;
/*
N.B. please remember to add new ones at the bottom
 */
public enum GroupLogType {
    GROUP_ADDED("group added"), // 0
    GROUP_REMOVED("group removed"), // 1
    GROUP_UPDATED("group updated"), // 2
    GROUP_RENAMED("group renamed"), // 3
    GROUP_MEMBER_ADDED("group member added"), // 4
    GROUP_MEMBER_REMOVED("group member removed"), // 5
    SUBGROUP_ADDED("sub-group added"), // 6
    SUBGROUP_REMOVED("sub-group removed"), // 7
    PERMISSIONS_CHANGED("permissions changed"), // 8
    REMINDER_DEFAULT_CHANGED("reminder default changed"), // 9
    DESCRIPTION_CHANGED("description changed"), // 10
    TOKEN_CHANGED("join code changed"), // 11
    DISCOVERABLE_CHANGED("discoverable setting changed"), // 12
    LANGUAGE_CHANGED("changed group default language"), // 13
    PARENT_CHANGED("added or changed parent group"), // 14
    MESSAGE_SENT("free form message sent"), // 15 todo : maybe consider shifting to an "account log" or similar
    GROUP_MEMBER_ADDED_VIA_JOIN_CODE("group member joined via join code"); // 16

    private final String text;

    private GroupLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
