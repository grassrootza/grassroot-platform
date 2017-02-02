package za.org.grassroot.integration.payments;

import za.org.grassroot.core.domain.AccountBillingRecord;

/**
 * Created by luke on 2016/10/26.
 */
public interface PaymentBroker {

    PaymentResponse asyncPaymentInitiate(String accountUid, PaymentMethod method, AccountBillingRecord amountToPay, String returnToUrl);

    PaymentResponse asyncPaymentCheckResult(String paymentId, String resourcePath);

    PaymentResponse initiateMobilePayment(AccountBillingRecord record, String notificationUrl);

    PaymentResponse checkMobilePaymentResult(String paymentId);

    void processAccountPaymentsOutstanding();

    String fetchDetailsForDirectDeposit(String accountUid);
}
