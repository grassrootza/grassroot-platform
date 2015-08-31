package za.org.grassroot.messaging.producer;

/**
 * Created by aakilomar on 8/31/15.
 */
public interface GenericJmsTemplateProducerService {

    public void sendWithNoReply(String destination, Object message);
}
