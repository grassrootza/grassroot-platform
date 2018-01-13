package za.org.grassroot.integration.messaging;

import java.util.List;

/**
 * Created by luke on 2017/05/23.
 */
public interface MessagingServiceBroker {

    /*
    First, pushing a notification right away (even if Async), instead of through notification, for OTPs and safety alerts
     */
    void sendSMS(String message, String userUid, boolean userRequested);

    MessageServicePushResponse sendPrioritySMS(String message, String destinationNumber);

    void sendEmail(List<String> addresses, GrassrootEmail grassrootEmail);

}
