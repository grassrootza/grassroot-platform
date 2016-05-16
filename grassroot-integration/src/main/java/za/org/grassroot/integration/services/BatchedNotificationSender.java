package za.org.grassroot.integration.services;

import org.springframework.stereotype.Service;

@Service
public interface BatchedNotificationSender {
	void processPendingNotifications();
}
