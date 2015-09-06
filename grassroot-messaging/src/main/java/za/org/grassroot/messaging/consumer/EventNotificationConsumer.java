package za.org.grassroot.messaging.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventChangeType;
import za.org.grassroot.core.event.EventChangeEvent;

import javax.swing.event.ChangeEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.logging.Logger;

import static za.org.grassroot.core.enums.EventChangeType.EVENT_ADDED;

/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class EventNotificationConsumer {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());


    //TODO recursive call for hierarchy
    @JmsListener(destination = "event-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void sendNewEventNotifications(Event event) {
        log.info("sendNewEventNotifications... <" + event.toString() + ">");
        for (User user : event.getAppliesToGroup().getGroupMembers()) {
            log.info("sendNewEventNotifications...send message to..." + user.getPhoneNumber());
        }
    }

    @JmsListener(destination = "event-changed", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendChangedEventNotifications(Event event) {
        log.info("sendChangedEventNotifications... <" + event.toString() + ">");

    }

    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(Event event) {
        log.info("sendCancelledEventNotifications... <" + event.toString() + ">");
    }



}

