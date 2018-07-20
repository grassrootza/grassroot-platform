package za.org.grassroot.webapp.model.rest;

/**
 * Created by luke on 2016/10/24.
 */
public class AutoCompleteResponse {

    private final String value;
    private final String label;

    public AutoCompleteResponse(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
