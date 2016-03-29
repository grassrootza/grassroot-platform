package za.org.grassroot.services;

import za.org.grassroot.core.enums.EventType;

public interface AsyncEventMessageSender {

    void sendNewMeetingNotifications(String meetingUid);
    void sendCancelMeetingNotifications(String meetingUid);
    void sendChangedEventNotification(String eventUid, EventType eventType, boolean startTimeChanged);

}
