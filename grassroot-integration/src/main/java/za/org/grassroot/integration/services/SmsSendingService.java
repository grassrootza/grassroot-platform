package za.org.grassroot.integration.services;

/**
 * Created by luke on 2015/09/09.
 */
public interface SmsSendingService {

    // todo: create a version that returns the result of the SMS as bound object rather than just raw XML
    public String sendSMS(String message, String destinationNumber);

}
