package za.org.grassroot.integration.payments;

import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/11/10.
 */
public class PaymentRedirectPP {

    private String url;
    private List<Map<String, String>> parameters;

    public PaymentRedirectPP() {
        // for JSON construction
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public List<Map<String, String>> getParameters() {
        return parameters;
    }

    public void setParameters(List<Map<String, String>> parameters) {
        this.parameters = parameters;
    }

    @Override
    public String toString() {
        return "PaymentRedirectPP{" +
                "url='" + url + '\'' +
                ", parameters=" + parameters +
                '}';
    }
}
