package za.org.grassroot.services.async;


import javax.jms.Message;

/**
 * Created by aakilomar on 8/31/15.
 */
public interface GenericJmsTemplateProducerService {

    void sendWithNoReply(String destination, Object message);

    Message receiveMessage(String destination);
}
