package za.org.grassroot.services.enums;

/**
 * Created by luke on 2016/02/15.
 */
public enum GroupPermissionTemplate {
    DEFAULT_GROUP("default_group"),
    CLOSED_GROUP("closed_group"),
    OPEN_GROUP("open_group");

    private final String text;

    private GroupPermissionTemplate(final String text) { this.text = text; }

    @Override
    public String toString() { return text; }

    public static GroupPermissionTemplate fromString(String template) {

        if (template != null) {
            for (GroupPermissionTemplate t : GroupPermissionTemplate.values()) {
                if (template.equalsIgnoreCase(t.text)) {
                    return t;
                }
            }
        }
        return GroupPermissionTemplate.DEFAULT_GROUP;
    }
}
