package za.org.grassroot.integration.router;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.integration.annotation.Router;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.dto.EventDTO;

/**
 * Created by paballo on 2016/04/06.
 */
@Component
public class OutboundMessageRouter {

    private Logger log = LoggerFactory.getLogger(OutboundMessageRouter.class);

    @Router(inputChannel="requestChannel")
    public String route(Message<EventDTO> message) {
        String route = (String) message.getHeaders().get("route");
        String outputChannel;
        switch(route){
            case "SMS":
                log.info("routing to sms channel");
                outputChannel= "smsOutboundChannel";
                break;
            case "ANDROID_APP":
                log.info("routing to gcm channel");
                outputChannel= "gcmOutboundChannel";
                break;
            default:
                outputChannel ="smsOutboundChannel";
                break;
        }

        return outputChannel;
    }

}
