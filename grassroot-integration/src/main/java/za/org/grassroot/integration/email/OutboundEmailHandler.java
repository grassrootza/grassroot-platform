package za.org.grassroot.integration.email;

import org.springframework.integration.annotation.MessageEndpoint;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import za.org.grassroot.core.domain.Notification;

/**
 * Created by paballo on 2016/06/28.
 */

@MessageEndpoint
public class OutboundEmailHandler {

    // private static final Logger log = LoggerFactory.getLogger(OutboundEmailHandler.class);

    // @Autowired
    // private NotificationService notificationService;

    @ServiceActivator(inputChannel = "emailOutboundChannel")
    public void handleMessage(Message<Notification> message) throws Exception {
        // todo : complete this
    }
}
