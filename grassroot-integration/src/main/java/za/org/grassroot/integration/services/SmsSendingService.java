package za.org.grassroot.integration.services;

/**
 * Created by luke on 2015/09/09.
 */
public interface SmsSendingService {

    // todo: create a version that returns the result of the SMS as bound object rather than just raw XML
    String sendSMS(String message, String destinationNumber);

    void sendPrioritySMS(String message, String destinationNumber);

    // helper method async wrapper for methods that need to call SMS send directly (i.e., bypassing notification because of time criticality)
    void sendAsyncSMS(String message, String destinationNumber);

}
