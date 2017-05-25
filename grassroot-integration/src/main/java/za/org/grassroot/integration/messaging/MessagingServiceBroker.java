package za.org.grassroot.integration.messaging;

import java.util.Set;

/**
 * Created by luke on 2017/05/23.
 */
public interface MessagingServiceBroker {

    /*
    First, pushing a notification right away (even if Async), instead of through notification, for OTPs and safety alerts
     */

    // helper method async wrapper for methods that need to call SMS send directly (i.e., bypassing notification because of time criticality)
    void sendSMS(String message, String destinationNumber);

    MessageServicePushResponse sendPrioritySMS(String message, String destinationNumber);

    /*
    Second, relaying some calls on group chat (in time these should all go straight to xmpp server, but for now)
     */
    void markMessagesAsRead(String groupUid, Set<String> messageUids);

    void updateActivityStatus(String userUid, String groupUid, boolean active, boolean userInitiated) throws Exception;

    void subscribeServerToGroupChatTopic(String groupUid);

}
