package za.org.grassroot.integration.payments.peachp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.HashMap;
import java.util.Map;

/**
 * Created by luke on 2016/11/01.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymentErrorPP {

    private String id;
    private PaymentResultPP result;

    private Map<String, String> card = new HashMap<>();
    private Map<String, String> risk = new HashMap<>();

    private String buildNumber;
    private String timestamp;
    private String ndc;

    public PaymentErrorPP() {
        // for jackson mapping
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public PaymentResultPP getResult() {
        return result;
    }

    public void setResult(PaymentResultPP result) {
        this.result = result;
    }

    public Map<String, String> getCard() {
        return card;
    }

    public void setCard(Map<String, String> card) {
        this.card = card;
    }

    public Map<String, String> getRisk() {
        return risk;
    }

    public void setRisk(Map<String, String> risk) {
        this.risk = risk;
    }

    public String getBuildNumber() {
        return buildNumber;
    }

    public void setBuildNumber(String buildNumber) {
        this.buildNumber = buildNumber;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public String getNdc() {
        return ndc;
    }

    public void setNdc(String ndc) {
        this.ndc = ndc;
    }

    @Override
    public String toString() {
        return "PaymentErrorPP{" +
                "\nid=" + id +
                ",\n result='" + result + '\'' +
                ",\n card=" + card +
                ",\n risk=" + risk +
                ",\n buildNumber='" + buildNumber + '\'' +
                ",\n timestamp='" + timestamp + '\'' +
                ",\n ndc='" + ndc + '\'' +
                '}';
    }
}
