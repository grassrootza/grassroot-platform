package za.org.grassroot.unit;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.GrassRootCoreConfig;
import za.org.grassroot.core.GrassRootApplicationProfiles;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.repository.LogBookRepository;
import za.org.grassroot.integration.GrassrootIntegrationConfig;
import za.org.grassroot.integration.domain.GcmEntity;
import za.org.grassroot.integration.xmpp.GcmPacketExtension;
import za.org.grassroot.integration.xmpp.GcmTransformer;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;


/**
 * Created by paballo on 2016/04/11.
 */
@SpringApplicationConfiguration(classes = {GcmTransformer.class,TestContextConfig.class})
@RunWith(SpringJUnit4ClassRunner.class)
@Transactional
@ActiveProfiles(GrassRootApplicationProfiles.INMEMORY)
public class GcmTransformerTest {

    private Logger log = LoggerFactory.getLogger(GcmTransformerTest.class);

    @Autowired
    private GcmTransformer gcmTransformer;




    @Test
    public void transformShouldWorkIfPush() throws  Exception {

        User user = new User("0828875097");
        Group group = new Group("test eventlog", user);
        Event event = new Meeting("test meeting",Instant.now(),  user, group, "someLoc");
        EventLog eventLog = new EventLog(user, event, EventLogType.EventNotification, "you are hereby invited to the test meeting", null);
        GcmRegistration gcmRegistration = new GcmRegistration(user,"xzf12", Instant.now());
        Notification notification = new Notification(user,eventLog,gcmRegistration,false,false, NotificationType.EVENT,eventLog.getMessage());
        Message<Notification> message = MessageBuilder.withPayload(notification).build();
        org.jivesoftware.smack.packet.Message transforemdMessage = gcmTransformer.transform(message);
        GcmPacketExtension packetExtension = (GcmPacketExtension)transforemdMessage.getExtension(GcmPacketExtension.GCM_NAMESPACE);
        ObjectMapper mapper =new ObjectMapper();
        GcmEntity entity = mapper.readValue(packetExtension.getJson(), GcmEntity.class);
        log.info(entity.toString());
        Map<String,String>  data = (HashMap)entity.getData();
        assertEquals(data.get("description"),notification.getMessage());


    }

    @Test
    public void  transformShouldWorkIfAcknowledgement() throws Exception{
        String messageId = "xxf5s";
        String registrationId = "xzf12";
        Notification notification = new Notification();
        User user = new User("0828875097");
        GcmRegistration gcmRegistration = new GcmRegistration(user,"xzf12", Instant.now());
        notification.setUid(messageId);
        notification.setMessageType("ack");
        notification.setGcmRegistration(gcmRegistration);
        Message<Notification> message = MessageBuilder.withPayload(notification).build();
        org.jivesoftware.smack.packet.Message transforemdMessage = gcmTransformer.transform(message);
        GcmPacketExtension packetExtension = (GcmPacketExtension)transforemdMessage.getExtension(GcmPacketExtension.GCM_NAMESPACE);
        ObjectMapper mapper =new ObjectMapper();
        GcmEntity entity = mapper.readValue(packetExtension.getJson(), GcmEntity.class);
        assertEquals(entity.getMessage_id(), notification.getUid());
        log.info(entity.toString());

    }

}


