/*
package za.org.grassroot.services.integration;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.jms.core.MessageCreator;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileSystemUtils;
import za.org.grassroot.GrassRootCoreConfig;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.event.EventChangeEvent;
import EventNotificationConsumer;
import za.org.grassroot.services.TestContextConfiguration;

import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.Session;
import java.io.File;
import java.sql.Timestamp;
import java.util.Date;


import static org.hamcrest.Matchers.*;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static com.jayway.awaitility.Awaitility.*;
import static com.jayway.awaitility.Duration.*;
import static java.util.concurrent.TimeUnit.*;

/**
 * @author Lesetse Kimwaga
 */
//@RunWith(SpringJUnit4ClassRunner.class)
//@ContextConfiguration(classes = {TestContextConfiguration.class, GrassRootCoreConfig.class})
//@Transactional
//public class EventNotificationConsumerTest {
//
//    private Logger log = LoggerFactory.getLogger(EventNotificationConsumerTest.class);
//    @Autowired
//    private ApplicationContext applicationContext;
//
//    @Autowired
//    private EventNotificationConsumer eventNotificationConsumer;
//
//    @Autowired
//    private  JmsTemplate jmsTemplate;
//
//    @Before
//    public void setUp() throws Exception {
//        // Clean out any ActiveMQ data from a previous run
//        FileSystemUtils.deleteRecursively(new File("activemq-data"));
//
//    }
//
//   @Test
//    public void testName() throws Exception {
//
//        Event event = new Event();
//        event.setName("JMS Check");
//        event.setCreatedDateTime(new Timestamp(System.currentTimeMillis()));
//
//        EventChangeEvent eventChangeEvent = new EventChangeEvent(event);
//        eventChangeEvent.setType(Event.class.getCanonicalName());
//
//        // Send a message
//        MessageCreator messageCreator = new MessageCreator() {
//            @Override
//            public Message createMessage(Session session) throws JMSException {
//                return session.createObjectMessage(eventChangeEvent);
//            }
//        };
//
//        jmsTemplate.send("event-added", messageCreator);
//
//         await().atMost(5, SECONDS).until(() -> assertThat(eventNotificationConsumer.getTestingInMemoryMessageStore(), is(not(empty()))));
//
//        *//*****************************************************************************
//         * The following test case should fail: We really expect something on the queue
//         *****************************************************************************//*
//        //await().atMost(5, SECONDS).until(() -> assertThat(eventNotificationConsumer.getTestingInMemoryMessageStore(),hasSize(equalTo(0))));
//
//
//    }
//}

