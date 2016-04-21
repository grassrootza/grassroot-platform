package za.org.grassroot.unit;

import org.jivesoftware.smack.packet.Message;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.integration.config.InfrastructureConfiguration;
import za.org.grassroot.integration.services.MessageSendingManager;
import za.org.grassroot.integration.xmpp.GcmPacketExtension;
import za.org.grassroot.integration.xmpp.GcmTransformer;
import za.org.grassroot.integration.xmpp.InboundGcmMessageHandler;

import javax.transaction.Transactional;

/**
 * Created by paballo on 2016/04/12.
 */

@SpringApplicationConfiguration(classes = {InfrastructureConfiguration.class, InboundGcmMessageHandler.class,TestContextConfig.class, MessageSendingManager.class})
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class IncomingGcmHandlerTest {

    @Autowired
    private InboundGcmMessageHandler messageHandler;

    private String registration =" {\n" +
            "      \"category\":\"com.techmorphosis.grassroot.gcm\",\n" +
            "      \"data\":\n" +
            "         {\n" +
            "         \"action\":\n" +
            "            \"com.techmorphosis.grassroot.gcm.REGISTER\",\n" +
            "         \"phoneNumber\":\"0616780986\"\n" +
            "         },\n" +
            "      \"message_id\":\"20\",\n" +
            "      \"from\":\"someRegistrationId\"\n" +
            "      }";

    @Test
    public void handleUpstream() throws Exception{
        Message message = new Message();
        message.addExtension(new GcmPacketExtension(registration));
        messageHandler.handleUpstreamMessage(message);
        
    }


}

