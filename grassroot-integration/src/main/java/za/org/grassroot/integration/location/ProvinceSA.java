package za.org.grassroot.integration.location;

public enum ProvinceSA {

    GP("gauteng"),
    NC("northern_cape"),
    WC("western_cape"),
    EC("eastern_cape"),
    KZN("kwazulu_natal"),
    LP("limpopo"),
    NW("north_west"),
    FS("free_state"),
    MP("mpumalanga");

    private final String key;

    ProvinceSA(String s) {
        this.key = s;
    }



    @Override
    public String toString() {
        return key;
    }

}