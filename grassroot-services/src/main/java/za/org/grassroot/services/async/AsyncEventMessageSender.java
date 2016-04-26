package za.org.grassroot.services.async;

import za.org.grassroot.core.enums.EventType;

public interface AsyncEventMessageSender {

	void sendFreeFormMessage(String sendingUserUid, String groupUid, String message);

}
