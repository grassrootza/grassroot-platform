package za.org.grassroot.integration.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Notification;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class MessageSendingManager implements MessageSendingService {

    @Autowired
    private MessageChannel requestChannel;

    @Override
    public void sendMessage(Notification notification) {
        Message<Notification> message = createMessage(notification, null);
        requestChannel.send(message);

    }

    @Override
    public void sendMessage(String destination, Notification notification) {
        Message<Notification> message = createMessage(notification, destination);
        requestChannel.send(message);
    }

    private Message<Notification> createMessage(Notification notification, String route) {
        if (route == null) {
            route = notification.getTarget().getMessagingPreference().name();
        }
        Message<Notification> message = MessageBuilder.withPayload(notification)
                .setHeader("route", route)
                .build();
        return message;
    }
}
