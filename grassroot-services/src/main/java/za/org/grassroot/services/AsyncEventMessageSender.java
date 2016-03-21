package za.org.grassroot.services;

import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.enums.EventType;

import java.util.Set;

public interface AsyncEventMessageSender {
    void sendNewMeetingNotifications(String meetingUid);
    void sendCancelMeetingNotifications(String meetingUid);
    void sendChangedEventNotification(String eventUid, EventType eventType, boolean startTimeChanged);
}
