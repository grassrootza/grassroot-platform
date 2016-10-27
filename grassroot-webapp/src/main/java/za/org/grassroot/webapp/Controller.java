package za.org.grassroot.webapp;

import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.web.socket.config.annotation.EnableWebSocket;

/**
 * Created by paballo on 2016/10/27.
 */

public class Controller {

    @MessageMapping("/messaging")
    @SendTo("/topic/{userUid}")
    public void sendMessage(@DestinationVariable("userUid") String userUid) throws Exception {
        //todo iterate over all unsent messages and send them

    }

}
