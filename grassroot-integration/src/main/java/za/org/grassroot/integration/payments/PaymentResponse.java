package za.org.grassroot.integration.payments;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2016/11/25.
 */
public class PaymentResponse {

    PaymentResultType type;
    String paymentId;

    protected PaymentResponse() {
        // for Spring / Jackson
    }

    protected PaymentResponse(PaymentResultType type, String paymentId) {
        this.type = type;
        this.paymentId = paymentId;
    }

    public boolean isSuccessful() { return false; }

    public String getReference() {
        return "";
    }

    public String getThisPaymentId() {
        return paymentId;
    }

    public PaymentResultType getType() {
        return type;
    }

    public String getDescription() {
        return "";
    }

    public String getRedirectUrl() { return ""; }

    public List<Map<String, String>> getRedirectParams() { return new ArrayList<>(); }

}
