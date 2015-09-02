package za.org.grassroot.messaging.consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
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

    private List<Object> testingInMemoryMessageStore = new ArrayList<>();


    @JmsListener(destination = "event-added", containerFactory = "messagingJmsContainerFactory",
            concurrency = "5")
    public void sendNewEventNotifications(EventChangeEvent message) {
        log.info("sendNewEventNotifications... <" + message.toString() + ">");
        testingInMemoryMessageStore.add(message);

    }

    @JmsListener(destination = "event-changed", containerFactory = "messagingJmsContainerFactory",
            concurrency = "3")
    public void sendChangedEventNotifications(EventChangeEvent message) {
        log.info("sendChangedEventNotifications... <" + message.toString() + ">");
        testingInMemoryMessageStore.add(message);

    }

    @JmsListener(destination = "event-cancelled", containerFactory = "messagingJmsContainerFactory",
            concurrency = "1")
    public void sendCancelledEventNotifications(EventChangeEvent message) {
        log.info("sendCancelledEventNotifications... <" + message.toString() + ">");
        testingInMemoryMessageStore.add(message);
    }

    /**
     * Strictly for testing This is fake Repository
     * @return
     */
    public List<Object> getTestingInMemoryMessageStore() {
        return testingInMemoryMessageStore;
    }

}

