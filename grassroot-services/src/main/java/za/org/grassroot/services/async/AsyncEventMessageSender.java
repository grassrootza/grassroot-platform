package za.org.grassroot.services.async;

import za.org.grassroot.core.enums.EventType;

public interface AsyncEventMessageSender {

    void sendNewMeetingNotifications(String meetingUid);
    void sendNewVoteNotifications(String voteUid);

    void sendCancelMeetingNotifications(String meetingUid);
    void sendChangedEventNotification(String eventUid, EventType eventType, boolean startTimeChanged);

    void sendFreeFormMessage(String sendingUserUid, String groupUid, String message);

}
