package za.org.grassroot.messaging.event;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationListener;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.event.EventChangeEvent;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;

import java.util.logging.Logger;

/**
 * Created by aakilomar on 8/30/15.
 */
@Component
public class EventChangeEventListener implements ApplicationListener<EventChangeEvent> {

    private Logger log = Logger.getLogger(getClass().getCanonicalName());

    @Autowired
    GenericJmsTemplateProducerService genericJmsTemplateProducerService;

    @Override
    public void onApplicationEvent(EventChangeEvent eventChangeEvent) {
        log.info(eventChangeEvent.toString());
        genericJmsTemplateProducerService.sendWithNoReply(eventChangeEvent.getType(),eventChangeEvent.getSource());

    }
}

