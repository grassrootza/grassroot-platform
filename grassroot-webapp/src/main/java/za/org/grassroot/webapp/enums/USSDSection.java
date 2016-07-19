package za.org.grassroot.webapp.enums;

/**
 * Created by luke on 2015/12/05.
 */
public enum USSDSection {

    BASE("ussd"),
    HOME ("home"),
    MEETINGS ("mtg"),
    VOTES ("vote"),
    LOGBOOK ("log"),
    GROUP_MANAGER ("group"),
    SAFETY_GROUP_MANAGER("safety"),
    USER_PROFILE ("user"),
    U404 ("error");

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

    public static USSDSection fromPath(String path) {

        // other way to do this might be to strip the suffix and pass to fromString, but seems to actually be one more calc,
        // hence doing it the verbose way (same below, with fromKey

        if (path != null) {
            for (USSDSection s : USSDSection.values()) {
                if (path.equalsIgnoreCase(s.section + pathSuffix)) {
                    return s;
                }
            }
        }
        return USSDSection.U404;
    }

    public String toKey() { return this.section + keySuffix; }

    public static USSDSection fromKey(String key) {

        if (key != null) {
            for (USSDSection s : USSDSection.values()) {
                if (key.equalsIgnoreCase(s.section + keySuffix)) {
                    return s;
                }
            }
        }
        return USSDSection.U404;
    }

}
