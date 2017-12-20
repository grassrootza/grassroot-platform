package za.org.grassroot.core.enums;

public enum Province {

    ZA_GP("GP"),
    ZA_NC("NC"),
    ZA_WC("WC"),
    ZA_EC("EC"),
    ZA_KZN("KZN"),
    ZA_LP("limpopo"),
    ZA_NW("north_west"),
    ZA_FS("free_state"),
    ZA_MP("mpumalanga");

    private final String key;

    Province(String s) {
        this.key = s;
    }

    @Override
    public String toString() {
        return key;
    }

}