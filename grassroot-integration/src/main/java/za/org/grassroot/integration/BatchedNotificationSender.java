package za.org.grassroot.integration;

import org.springframework.stereotype.Service;

@Service
public interface BatchedNotificationSender {
	void processPendingNotifications();
}
