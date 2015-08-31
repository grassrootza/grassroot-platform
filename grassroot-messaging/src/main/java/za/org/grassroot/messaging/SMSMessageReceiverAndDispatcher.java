package za.org.grassroot.messaging;

import java.io.File;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.springframework.util.FileSystemUtils;
import org.springframework.web.client.RestTemplate;

@Component
public class SMSMessageReceiverAndDispatcher {

    private Logger log = LoggerFactory.getLogger(SMSMessageReceiverAndDispatcher.class);


    @JmsListener(destination = "sms-destination", containerFactory = "messagingJmsContainerFactory")
    public void receiveMessage(String message) {
        log.info("Received <" + message + ">");

    }
}
