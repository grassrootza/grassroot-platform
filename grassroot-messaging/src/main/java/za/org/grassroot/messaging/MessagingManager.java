package za.org.grassroot.messaging;

import org.springframework.stereotype.Service;
import za.org.grassroot.messaging.domain.MessagePublishRequest;
import za.org.grassroot.messaging.domain.MessagePublishResultResult;

/**
 * @author Lesetse Kimwaga
 */

@Service
public class MessagingManager implements  MessagingService{

    @Override
    public MessagePublishResultResult publish(MessagePublishRequest messagePublishRequest) {

        if(messagePublishRequest.getMessageProtocol() != null)
        {

            switch (messagePublishRequest.getMessageProtocol())
            {
                case SMS:

                    break;
            }
        }
        else
        {
            //send to all protocols?
        }
        return null;
    }
}
