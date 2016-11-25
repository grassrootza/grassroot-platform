package za.org.grassroot.integration.exception;

import za.org.grassroot.integration.payments.peachp.PaymentErrorPP;

/**
 * Created by luke on 2016/11/14.
 */
public class PaymentMethodFailedException extends RuntimeException {

    private PaymentErrorPP paymentError;

    public PaymentMethodFailedException(PaymentErrorPP paymentError) {
        this.paymentError = paymentError;
    }

    public PaymentErrorPP getPaymentError() {
        return paymentError;
    }

}
