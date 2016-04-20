package za.org.grassroot.unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.NotificationType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.integration.config.InfrastructureConfiguration;
import za.org.grassroot.integration.router.OutboundMessageRouter;

import static org.junit.Assert.assertEquals;

/**
 * Created by paballo on 2016/04/11.
 */
@ContextConfiguration(classes = {InfrastructureConfiguration.class, OutboundMessageRouter.class})
@RunWith(SpringJUnit4ClassRunner.class)
public class OutboundRouterTest {

    @Autowired
    protected MessageChannel requestChannel;

    @Autowired
    protected MessageChannel smsOutboundChannel;

    @Autowired
    protected MessageChannel gcmOutboundChannel;

    @Autowired
    protected OutboundMessageRouter outboundMessageRouter;

    @Test
    public void routingToSmsShouldWork() throws Exception{
        Notification payload = new Notification(new User("42342342"), null, true, true, NotificationType.GENERAL);
        Message<Notification> message = MessageBuilder.withPayload(payload)
                .setHeader("route",UserMessagingPreference.SMS.toString()).build();
         assertEquals("smsOutboundChannel", outboundMessageRouter.route(message));

    }

    @Test
    public void routingToGcmShouldWork() throws Exception{
        Notification payload = new Notification(new User("42342342"), null, true, true, NotificationType.GENERAL);
        Message<Notification> message = MessageBuilder.withPayload(payload)
                .setHeader("route",UserMessagingPreference.ANDROID_APP.toString()).build();
        assertEquals("gcmOutboundChannel", outboundMessageRouter.route(message));

    }

    @Test
    public void routingToSmsShouldWorkWhenRouteIsNull() throws Exception{
        Notification payload = new Notification(new User("42342342"), null, true, true, NotificationType.GENERAL);
        Message<Notification> message = MessageBuilder.withPayload(payload).build();
        assertEquals("smsOutboundChannel",outboundMessageRouter.route(message));

    }



}

