package za.org.grassroot.integration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.support.MessageBuilder;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.core.repository.GcmRegistrationRepository;

import java.util.Arrays;
import java.util.List;

/**
 * Created by luke on 2015/09/09.
 */
@Service
public class MessageSendingManager implements MessageSendingService {

    private static final Logger logger = LoggerFactory.getLogger(MessageSendingManager.class);

    @Autowired
    private MessageChannel requestChannel;

    @Autowired
    private GcmRegistrationRepository gcmRegistrationRepository;

    @Autowired(required = false)
    private MqttPahoMessageDrivenChannelAdapter mqttAdapter;

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

    @Override
    public void sendPollingMessage(){
        requestChannel.send(MessageBuilder.withPayload("polling").setHeader("route", "ANDROID_APP").build());
        if (mqttAdapter != null) {
            List<String> topicsSubscribedTo = Arrays.asList(mqttAdapter.getTopic());
            if (!topicsSubscribedTo.contains("Grassroot")) {
                mqttAdapter.addTopic("Grassroot", 1);
            }
        }
    }

    private Message<Notification> createMessage(Notification notification, String givenRoute) {
        String route = (givenRoute == null) ? notification.getTarget().getMessagingPreference().name() : givenRoute;
        if ("ANDROID_APP".equals(route)) {
            if (!checkGcmRegistration(notification)) {
                route = "SMS";
            }
        }

        return MessageBuilder.withPayload(notification)
                .setHeader("route", route)
                .build();
    }

    @Transactional
    private boolean checkGcmRegistration(Notification notification) {
        User user = notification.getTarget();
        if (gcmRegistrationRepository.findTopByUserOrderByCreationTimeDesc(user) != null) {
            return true;
        } else {
            logger.error("user had preference set to Android, but no GCM registration, correcting");
            user.setMessagingPreference(UserMessagingPreference.SMS);
            return false;
        }
    }


}
