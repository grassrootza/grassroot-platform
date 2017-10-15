package za.org.grassroot.services.geo;

public enum GeographicSearchType {

    PUBLIC(1),
    PRIVATE(-1),
    BOTH(0);

    private final int utilityCode;

    GeographicSearchType(final int utilityCode) {
        this.utilityCode = utilityCode;
    }

    public int toInt() { return utilityCode; }

}
