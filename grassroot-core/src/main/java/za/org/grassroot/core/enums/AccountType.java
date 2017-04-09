package za.org.grassroot.core.enums;

/**
 * Created by luke on 2016/10/04.
 */
public enum AccountType {

    FREE,
    LIGHT,
    STANDARD,
    LARGE,
    HEAVY,
    ENTERPRISE;

    public static boolean contains(String s) {
        for (AccountType type : values()) {
            if (type.name().equals(s)) {
                return true;
            }
        }
        return false;
    }

}
