package za.org.grassroot.core.enums;


public enum MessageVariationAssignment {
    
    DEFAULT("DEFAULT"),
    EXPERIMENT("EXPERIMENT"),
    CONTROL("CONTROL"),
    UNASSIGNED("UNASSIGNED");

    private final String text;

    MessageVariationAssignment(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
