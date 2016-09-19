package za.org.grassroot.unit;

import org.jivesoftware.smack.packet.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.integration.xmpp.GcmPacketExtension;
import za.org.grassroot.integration.xmpp.InboundGcmMessageHandler;

/**
 * Created by paballo on 2016/04/12.
 */

@RunWith(SpringRunner.class) // todo : clean up config in the next
@ContextConfiguration(classes = {TestContextConfig.class, InboundGcmMessageHandler.class})
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
public class IncomingGcmHandlerTest {

    private static final Logger log = LoggerFactory.getLogger(IncomingGcmHandlerTest.class);

    @Autowired
    private InboundGcmMessageHandler messageHandler;

    @Autowired
    private UserRepository userRepository;

    private String registration =" {\n" +
            "      \"category\":\"com.techmorphosis.grassroot.gcm\",\n" +
            "      \"data\":\n" +
            "         {\n" +
            "         \"action\":\n" +
            "            \"REGISTER\",\n" +
            "         \"phoneNumber\":\"0616780986\"\n" +
            "         },\n" +
            "      \"message_id\":\"20\",\n" +
            "      \"from\":\"someRegistrationId\"\n" +
            "      }";

    @Test
    public void handleUpstream() throws Exception{
        User user = userRepository.save(new User("0616780986", "some name"));
        log.info("Constructed user={}", user);
        user = userRepository.save(user);
        log.info("Created and saved user={}", user);
      //  gcmService.registerUser(new User("0616780986"),"someRegistrationId");
        Message message = new Message();
        message.addExtension(new GcmPacketExtension(registration));
      //  messageHandler.handleUpstreamMessage(message);
        
    }


}

