package za.org.grassroot.unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.GrassrootApplicationProfiles;
import za.org.grassroot.integration.xmpp.NotificationToGcmXmppTransformer;


/**
 * Created by paballo on 2016/04/11.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {NotificationToGcmXmppTransformer.class,TestContextConfig.class})
@ActiveProfiles(GrassrootApplicationProfiles.INMEMORY)
@ConditionalOnProperty(name = "gcm.connection.enabled", havingValue = "true",  matchIfMissing = false)
public class NotificationToGcmXmppTransformerTest {

    private Logger log = LoggerFactory.getLogger(NotificationToGcmXmppTransformerTest.class);

    @Autowired(required = false)
    private NotificationToGcmXmppTransformer notificationToGcmXmppTransformer;


    @Test
    public void transformShouldWorkIfPush() throws  Exception {

        //Test will fail because of og integration testing issues

      /*  User user = new User("0828875097");
        Group group = new Group("test eventlog", user);
        Event event = new Meeting("test meeting",Instant.now(),  user, group, "someLoc");
        EventLog eventLog = new EventLog(user, event, EventLogType.CREATED, "you are hereby invited to the test meeting", null);
        GcmRegistration gcmRegistration = new GcmRegistration(user,"xzf12", Instant.now());
        Notification notification = new Notification(user,eventLog, false,false, NotificationType.EVENT);
        Message<Notification> message = MessageBuilder.withPayload(notification).build();
        org.jivesoftware.smack.packet.Message transforemdMessage = notificationToGcmXmppTransformer.transform(message).getPayload();
        GcmPacketExtension packetExtension = (GcmPacketExtension)transforemdMessage.getExtension(GcmPacketExtension.GCM_NAMESPACE);
        ObjectMapper mapper =new ObjectMapper();
        GcmEntity entity = mapper.readValue(packetExtension.getJson(), GcmEntity.class);
        log.info(entity.toString());
        Map<String,Object> data = entity.getData();
        assertEquals(data.get("description"),notification.getMessage());*/


    }
}


