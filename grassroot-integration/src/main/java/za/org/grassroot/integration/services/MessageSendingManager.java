package za.org.grassroot.integration.services;

import org.springframework.stereotype.Service;
import za.org.grassroot.integration.domain.MessageProtocol;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class MessageSendingManager implements MessageSendingService {

    private SmsSendingService smsSender;

    @Override
    public String sendMessage(String message, String destination, MessageProtocol messageProtocol) {

        // todo: replace with an object
        String messageResponse;

        switch (messageProtocol) {
            case SMS:
                messageResponse = smsSender.sendSMS(message, destination);
                break;
            default:
                messageResponse = smsSender.sendSMS(message, destination);
                break;
        }

        return messageResponse;

    }
}
