package za.org.grassroot.messaging.producer;


import javax.jms.Message;

/**
 * Created by aakilomar on 8/31/15.
 */
public interface GenericJmsTemplateProducerService {

    public void sendWithNoReply(String destination, Object message);

    Message receiveMessage(String destination);
}
