package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import za.org.grassroot.integration.domain.MessageProtocol;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class MessageSendingManager implements MessageSendingService {

    @Autowired
    private SmsSendingService smsSender;

    @Override
    public String sendMessage(String message, String destination, MessageProtocol messageProtocol) {

        // todo: replace with an object
        String messageResponse;

        if (messageProtocol != null) {
            switch (messageProtocol) {
                case SMS:
                    messageResponse = smsSender.sendSMS(message, destination);
                    break;
                default:
                    messageResponse = smsSender.sendSMS(message, destination);
                    break;
            }
        } else {
            messageResponse = smsSender.sendSMS(message, destination);
        }

        return messageResponse;

    }
}
