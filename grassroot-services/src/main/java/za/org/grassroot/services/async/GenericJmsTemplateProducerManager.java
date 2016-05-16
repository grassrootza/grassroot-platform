package za.org.grassroot.services.async;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.stereotype.Component;

import javax.jms.Message;


/**
 * Created by aakilomar on 8/31/15.
 */
@Component
public class GenericJmsTemplateProducerManager implements GenericJmsTemplateProducerService {

    @Autowired
    JmsTemplate jmsTemplate;

    @Override
    public void sendWithNoReply(String destination, Object message) {
        jmsTemplate.setDeliveryPersistent(true);
        jmsTemplate.convertAndSend(destination, message);
    }
}
