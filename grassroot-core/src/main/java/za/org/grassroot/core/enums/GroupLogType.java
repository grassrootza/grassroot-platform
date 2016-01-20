package za.org.grassroot.core.enums;

public enum GroupLogType {
    GROUP_ADDED("group added"),
    GROUP_REMOVED("group removed"),
    GROUP_MEMBER_ADDED("group member added"),
    GROUP_MEMBER_REMOVED("group member removed"),
    SUBGROUP_ADDED("sub-group added"),
    SUBGROUP_REMOVED("sub-group removed");

    private final String text;

    private GroupLogType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }

}
