package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.MessageProtocol;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class MessageSendingManager implements MessageSendingService {

    @Autowired
    private SmsSendingService smsSender;
    @Autowired
    MessageChannel requestChannel;


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

    @Override
    public void sendMessage(Notification notification) {
        String route = notification.getUser().getMessagingPreference().name();
        requestChannel.send(createMessage(notification,route));

    }

    @Override
    public void sendMessage(String destination, Notification notification) {
       requestChannel.send(createMessage(notification,destination));
    }


    private Message<Notification> createMessage(Notification notification, String route){
        if(route.equals(null)){route = notification.getUser().getMessagingPreference().name();}
        Message<Notification> message = MessageBuilder.withPayload(notification)
                .setHeader("route",route).
                        build();

        return message;

    }


}
