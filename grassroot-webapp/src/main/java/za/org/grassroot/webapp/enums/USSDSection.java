package za.org.grassroot.webapp.enums;

/**
 * Created by luke on 2015/12/05.
 */
public enum USSDSection {

    BASE("ussd"),
    HOME ("home"),
    MEETINGS ("mtg"),
    VOTES ("vote"),
    TODO("todo"),
    GROUP_MANAGER ("group"),
    SAFETY_GROUP_MANAGER("safety"),
    USER_PROFILE ("user"),
    LIVEWIRE("livewire"),
    U404 ("error"),
    MORE ("more");

    private final String section;

    private static final String pathSuffix = "/";
    private static final String keySuffix = ".";

    USSDSection(String p) { section = p; }

    public static USSDSection fromString(String section) {

        if (section != null) {
            for (USSDSection s : USSDSection.values()) {
                if (section.equalsIgnoreCase(s.section)) {
                    return s;
                }
            }
        }
        return USSDSection.U404;
    }

    public String toString() { return this.section; }

    public String toPath() { return this.section + pathSuffix; }

    public String toKey() { return this.section + keySuffix; }

}
