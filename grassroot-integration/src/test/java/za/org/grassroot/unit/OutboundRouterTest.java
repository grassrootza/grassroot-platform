package za.org.grassroot.unit;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringRunner;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.notification.EventCancelledNotification;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.integration.router.OutboundMessageRouter;

import static org.junit.Assert.assertEquals;

/**
 * Created by paballo on 2016/04/11.
 */
@RunWith(SpringRunner.class)
@ContextConfiguration(classes = {TestContextConfig.class, OutboundMessageRouter.class})
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
      //  EventLog eventLog = new EventLog(new User(""), new Meeting("Dummy Meeting", Instant.now(), new User("433"), null, "johannesburg"), EventLogType.TEST, "message", UserMessagingPreference.SMS);

        EventLog eventLog = new EventLog(new User(""), Meeting.makeEmpty(new User("")), EventLogType.TEST);

        Notification payload = new EventCancelledNotification(new User("42342342"), "blah", eventLog);
        Message<Notification> message = MessageBuilder.withPayload(payload)
                .setHeader("route", UserMessagingPreference.SMS.toString()).build();
         assertEquals("smsOutboundChannel", outboundMessageRouter.route(message));

    }

    @Test
    public void routingToGcmShouldWork() throws Exception{

        EventLog eventLog = new EventLog(new User(""), Meeting.makeEmpty(new User("")), EventLogType.TEST);

     //   EventLog eventLog = new EventLog(new User(""), new Meeting("Dummy Meeting", Instant.now(), new User("433"), null, "johannesburg"),
          //      EventLogType.TEST, "message",
             //   UserMessagingPreference.ANDROID_APP);

        Notification payload = new EventCancelledNotification(new User("42342342"), "blah", eventLog);
        Message<Notification> message = MessageBuilder.withPayload(payload)
                .setHeader("route",UserMessagingPreference.ANDROID_APP.toString()).build();
        assertEquals("gcmOutboundChannel", outboundMessageRouter.route(message));

    }

    @Test
    public void routingToSmsShouldWorkWhenRouteIsNull() throws Exception{
        EventLog eventLog = new EventLog(new User(""), Meeting.makeEmpty(new User("")), EventLogType.TEST);

        Notification payload = new EventCancelledNotification(new User("42342342"), "blah", eventLog);
        Message<Notification> message = MessageBuilder.withPayload(payload).build();
        assertEquals("smsOutboundChannel",outboundMessageRouter.route(message));

    }



}

