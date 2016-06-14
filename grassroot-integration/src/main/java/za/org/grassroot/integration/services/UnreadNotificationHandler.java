package za.org.grassroot.integration.services;

/**
 * Created by luke on 2016/06/14.
 * lightweight class to handle resending of unread notifications.
 * note: all sms notifications are automatically marked "read", as that is most secure/reliable message sending mechanism.
 * so this is primarily for handling Android messages that are not read.
 * todo: probably also want to add an urgency filter, to this and to notifications.
 */
public interface UnreadNotificationHandler {

    void processUnreadNotifications();
}
