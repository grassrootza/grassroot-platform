package za.org.grassroot.messaging;

import za.org.grassroot.messaging.domain.MessagePublishRequest;
import za.org.grassroot.messaging.domain.MessagePublishResultResult;

/**
 * @author Lesetse Kimwaga
 */
public interface MessagingService {

    MessagePublishResultResult publish(MessagePublishRequest messagePublishRequest);
}
