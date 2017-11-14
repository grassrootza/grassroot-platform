package za.org.grassroot.core.domain.campaign;


public enum CampaignType {

    Aquisition("Aquisition"),
    Petition("Petition"),
    Information("Information");

    private final String text;

    CampaignType(final String text) {
        this.text = text;
    }

    @Override
    public String toString() {
        return text;
    }
}
