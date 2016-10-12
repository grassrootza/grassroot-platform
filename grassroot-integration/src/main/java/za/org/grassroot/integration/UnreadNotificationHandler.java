package za.org.grassroot.integration;

/**
 * Created by luke on 2016/06/14.
 * lightweight class to handle resending of unread notifications.
 * note: all sms notifications are automatically marked "read", as that is most secure/reliable message sending mechanism.
 * so this is primarily for handling Android messages that are not read.
 */
public interface UnreadNotificationHandler {

    void processUnreadNotifications();
}
