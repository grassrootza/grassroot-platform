package za.org.grassroot.services;

import za.org.grassroot.core.domain.GroupLog;

import java.util.Set;

public interface AsyncEventMessageSender {
    void sendNewMeetingNotifications(String meetingUid);
}
