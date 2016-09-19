package za.org.grassroot.integration.sms;

/**
 * Created by luke on 2015/09/09.
 */
public interface SmsSendingService {

    SmsGatewayResponse sendSMS(String message, String destinationNumber);

    SmsGatewayResponse sendPrioritySMS(String message, String destinationNumber);

    // helper method async wrapper for methods that need to call SMS send directly (i.e., bypassing notification because of time criticality)
    void sendAsyncSMS(String message, String destinationNumber);

}
