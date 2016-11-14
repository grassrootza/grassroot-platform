package za.org.grassroot.integration.payments;

import java.util.Map;

/**
 * Created by luke on 2016/10/31.
 */
public class PaymentResponsePP {

    private String id;
    private Double amount;
    private String registrationId;
    private String paymentType;
    private String paymentBrand;

    private PaymentResultPP result;
    private PaymentRedirectPP redirect;
    private Map<String, String> risk;

    public PaymentResponsePP() {
        // for Spring/Jackson
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(String registrationId) {
        this.registrationId = registrationId;
    }

    public String getPaymentType() {
        return paymentType;
    }

    public void setPaymentType(String paymentType) {
        this.paymentType = paymentType;
    }

    public String getPaymentBrand() {
        return paymentBrand;
    }

    public void setPaymentBrand(String paymentBrand) {
        this.paymentBrand = paymentBrand;
    }

    public PaymentResultPP getResult() {
        return result;
    }

    public void setResult(PaymentResultPP result) {
        this.result = result;
    }

    public Map<String, String> getRisk() {
        return risk;
    }

    public void setRisk(Map<String, String> risk) {
        this.risk = risk;
    }

    public Double getAmount() {
        return amount;
    }

    public void setAmount(Double amount) {
        this.amount = amount;
    }

    public void setRedirect(PaymentRedirectPP redirect) { this.redirect = redirect; }

    public PaymentRedirectPP getRedirect() { return redirect; }

    @Override
    public String toString() {
        return "PaymentResponsePP{" +
                "id='" + id + '\'' +
                ", registrationId='" + registrationId + '\'' +
                ", paymentType='" + paymentType + '\'' +
                ", paymentBrand='" + paymentBrand + '\'' +
                ", redirect=" + redirect +
                ", result=" + result +
                ", risk=" + risk +
                '}';
    }
}
