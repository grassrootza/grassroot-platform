package za.org.grassroot.integration.payments;

/**
 * Created by luke on 2016/11/25.
 */
public class PaymentResponse {

    PaymentResultType type;
    String paymentId;

    public PaymentResponse() {
        // for Spring / Jackson
    }

    public PaymentResponse(PaymentResultType type, String paymentId) {
        this.type = type;
        this.paymentId = paymentId;
    }

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

}
