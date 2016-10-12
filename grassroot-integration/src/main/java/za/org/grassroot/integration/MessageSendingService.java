package za.org.grassroot.integration;

import za.org.grassroot.core.domain.Notification;

/**
 * Created by luke on 2015/09/09.
 */
public interface MessageSendingService {

	void sendMessage(Notification notification);

	void sendMessage(String destination, Notification notification);

    void sendPollingMessage();
}
