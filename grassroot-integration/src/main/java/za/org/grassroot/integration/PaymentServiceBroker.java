package za.org.grassroot.integration;

import za.org.grassroot.integration.domain.PaymentMethod;

/**
 * Created by luke on 2016/10/26.
 */
public interface PaymentServiceBroker {

    // todo : will have to work out integration here once have firmed up payments provider
    // return true/false depending on whether payment succeeded (though may want this to be a response)
    boolean linkPaymentMethodToAccount(PaymentMethod method, String accountUid);

    void processAccountPaymentsOutstanding();
}
