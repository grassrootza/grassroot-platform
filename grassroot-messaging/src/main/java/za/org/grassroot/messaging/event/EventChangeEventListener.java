package za.org.grassroot.messaging.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.event.EventChangeEvent;
import za.org.grassroot.messaging.producer.GenericJmsTemplateProducerService;


/**
 * Created by aakilomar on 8/30/15.
 */
@Component
public class EventChangeEventListener implements ApplicationListener<EventChangeEvent> {

    private Logger log = LoggerFactory.getLogger(getClass().getCanonicalName());

    @Autowired
    GenericJmsTemplateProducerService genericJmsTemplateProducerService;

    @Override
    public void onApplicationEvent(EventChangeEvent eventChangeEvent) {
        log.info("onApplicationEvent..." + eventChangeEvent.toString());
        genericJmsTemplateProducerService.sendWithNoReply(eventChangeEvent.getType(), eventChangeEvent.getSource());

    }
}

