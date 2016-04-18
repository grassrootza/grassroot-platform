package za.org.grassroot.integration.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.Router;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Notification;

/**
 * Created by paballo on 2016/04/06.
 */
@Component
public class OutboundMessageRouter {

    private Logger log = LoggerFactory.getLogger(OutboundMessageRouter.class);

    @Router(inputChannel="requestChannel")
    public String route(Message<Notification> message) {
        String route = (String) message.getHeaders().get("route");
        String outputChannel = null;
        if(route !=null) {
            switch (route) {
                case "SMS":
                    log.info("routing to sms channel");
                    outputChannel = "smsOutboundChannel";
                    break;
                case "ANDROID_APP":
                    log.info("routing to gcm channel");
                    outputChannel = "gcmOutboundChannel";
                    break;
                default:
                    log.info("badly form route={}, defaulting to sms channel", route);
                    outputChannel = "smsOutboundChannel";
            }
        }else{
             log.info("Route not specified defaulting to sms");
             outputChannel = "smsOutboundChannel";
        }


         return outputChannel;
    }

}
