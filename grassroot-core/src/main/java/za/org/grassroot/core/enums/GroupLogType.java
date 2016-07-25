package za.org.grassroot.core.enums;
/*
N.B. please remember to add new ones at the bottom
todo : switch this to string based enum, not ordinal
 */
public enum GroupLogType {
    GROUP_ADDED("group added"),
    GROUP_REMOVED("group removed"),
    GROUP_UPDATED("group updated"),
    GROUP_RENAMED("group renamed"),
    GROUP_MEMBER_ADDED("group member added"),
    GROUP_MEMBER_REMOVED("group member removed"),
    SUBGROUP_ADDED("sub-group added"),
    SUBGROUP_REMOVED("sub-group removed"),
    PERMISSIONS_CHANGED("permissions changed"),
    REMINDER_DEFAULT_CHANGED("reminder default changed"),
    DESCRIPTION_CHANGED("description changed"),
    TOKEN_CHANGED("join code changed"),
    DISCOVERABLE_CHANGED("discoverable setting changed"),
    LANGUAGE_CHANGED("changed group default language"),
    PARENT_CHANGED("added or changed parent group"),
    MESSAGE_SENT("free form message sent"), // todo : maybe consider shifting to an "account log" or similar
    GROUP_MEMBER_ADDED_VIA_JOIN_CODE("group member joined via join code"),
    GROUP_MEMBER_ADDED_AT_CREATION("Group created"),
    GROUP_AVATAR_UPLOADED("Group avatar loaded"),
    GROUP_AVATAR_REMOVED("Group avatar deleted"),
    GROUP_DEFAULT_IMAGE_CHANGED("Group default image changed");

    private final String text;

    GroupLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
