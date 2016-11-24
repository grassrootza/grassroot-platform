package za.org.grassroot.integration.payments;

import za.org.grassroot.core.domain.AccountBillingRecord;

/**
 * Created by luke on 2016/10/26.
 */
public interface PaymentServiceBroker {

    // return true/false depending on whether payment succeeded (though may want this to be a response)
    boolean linkPaymentMethodToAccount(PaymentMethod method, String accountUid,
                                       AccountBillingRecord billingRecord, boolean deleteBillOnFailure);

    PaymentRedirectPP asyncPaymentInitiate(PaymentMethod method, double amountToPay, String returnToUrl);

    PaymentResultType asyncPaymentCheckResult(String paymentId, String resourcePath);

    void recordPaymentAsSuccessful(String paymentId, PaymentResponsePP responsePP);

    void processAccountPaymentsOutstanding();
}
