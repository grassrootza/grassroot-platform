package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.notification.WelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;
import za.org.grassroot.webapp.controller.rest.incoming.IncomingSMSController;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by paballo on 2016/02/18.
 */
public class IncomingSMSControllerTest extends RestAbstractUnitTest {

    private static final String path = "/api/inbound/sms/";
    private Event meeting;

    @Mock
    private MessageAssemblingService messageAssemblingService;

    @Mock
    private MessagingServiceBroker messagingServiceBroker;

    @Mock
    private UserResponseBroker userResponseBrokerMock;

    @Mock
    private LogsAndNotificationsBroker logsAndNotificationsBrokerMock;

    @InjectMocks
    private IncomingSMSController aatIncomingSMSController;

    @Before
    public void setUp() {
        mockMvc =   MockMvcBuilders.standaloneSetup(aatIncomingSMSController).build();
    }

    private Page<Notification> dummyNotification(User target, Event event) {
        Notification notification = new EventInfoNotification(target, "hello",
                new EventLog(target, event, EventLogType.CREATED));
        return new PageImpl<>(Collections.singletonList(notification));
    }

    /**
     * In this case user sent "yes" message and there is an outstanding meeting
     * We are verifying that:
     * <ul>
     * <li> rsvp of Type YES will be recorded for this meeting</li>
     * <li> no vote will be recorded </li>
     * <li> no user log will be recorded </li>
     * <li> no group log will be recorded </li>
     * <li> recent notifications will NOT be checked </li>
     * </ul>
     */
    @Test
    public void validResponseToMeeting() throws Exception {

        meeting = meetingEvent;
        meeting.setRsvpRequired(true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(userResponseBrokerMock.checkForEntityForUserResponse(sessionTestUser.getUid(), false)).thenReturn(meeting);
        when(userResponseBrokerMock.checkValidityOfResponse(meeting, "yes")).thenReturn(true);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(dummyNotification(sessionTestUser, meeting));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", "yes"))
                .andExpect(status().isOk());

        verify(userResponseBrokerMock, times(1)).checkForEntityForUserResponse(sessionTestUser.getUid(), false);
        verify(userResponseBrokerMock, times(1)).checkValidityOfResponse(meeting, "yes");
        verify(userResponseBrokerMock, times(1)).recordUserResponse(sessionTestUser.getUid(), JpaEntityType.MEETING,
                meeting.getUid(), "yes");
        verifyZeroInteractions(eventBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
        verifyZeroInteractions(messageAssemblingService);
        verifyZeroInteractions(messagingServiceBroker);
    }


    /**
     * In this case user sent unexpected message while there is an outstanding meeting
     * No notifications sent to user in last six hours
     * We are verifying that:
     * <ul>
     * <li> no rsvp of will be recorded for this meeting</li>
     * <li> no vote will be recorded </li>
     * <li> user log WILL be recorded </li>
     * <li> recent notifications will be checked </li>
     * <li> no group log will be recorded </li>
     * </ul>
     */
    @Test
    public void invalidResponseToMeeting() throws Exception {
        String msg = "something";
        meeting = meetingEvent;
        meeting.setRsvpRequired(true);
        EventLog eventLog = new EventLog(sessionTestUser, meeting, EventLogType.CREATED);
        EventInfoNotification notification = new EventInfoNotification(sessionTestUser, "meeting called", eventLog);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(userResponseBrokerMock.checkForEntityForUserResponse(sessionTestUser.getUid(), false)).thenReturn(meeting);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(notification)));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(logsAndNotificationsBrokerMock, times(1)).asyncStoreBundle(any(LogsAndNotificationsBundle.class));
        verifyZeroInteractions(eventLogBrokerMock);
    }


    /**
     * In this case user sent "yes" message and there is no outstanding votes but THERE IS an outstanding vote
     * We are verifying that:
     * <ul>
     *  <li> vote response "yes" will be recorded </li>
     *  <li> no rsvp will be recorded</li>
     *  <li> no user log will be recorded </li>
     *  <li> no group log will be recorded </li>
     *  <li> recent notifications will NOT be checked </li>
     * </ul>
     */
    @Test
    public void validResponseToYesNoVote() throws Exception {

        String msg = "yes";
        Vote vote = createVote(null);
        vote.setRsvpRequired(true);
        List<Event> votes = Collections.singletonList(vote);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(userResponseBrokerMock.checkForEntityForUserResponse(sessionTestUser.getUid(), false)).thenReturn(vote);
        when(userResponseBrokerMock.checkValidityOfResponse(vote, msg)).thenReturn(true);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(dummyNotification(sessionTestUser, vote));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userResponseBrokerMock, times(1)).recordUserResponse(sessionTestUser.getUid(), JpaEntityType.VOTE,
                vote.getUid(), msg);
        verify(userResponseBrokerMock, times(1)).checkValidityOfResponse(vote, msg);
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
    }



    /**
     * In this case user sent message "TWO" and there is an outstanding vote having this option
     * We are verifying that:
     * <ul>
     *  <li> vote response for this option will be recorded </li>
     *  <li> no rsvp will be recorded</li>
     *  <li> no user log will be recorded </li>
     *  <li> no group log will be recorded </li>
     *  <li> recent notifications will NOT be checked </li>
     * </ul>
     */
    @Test
    public void validResponseToCustomOptionsVote() throws Exception {

        String msg = "TWO";
        Vote vote = createVote(new String[]{"one", "two", "three"});
        vote.setRsvpRequired(true);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(userResponseBrokerMock.checkForEntityForUserResponse(sessionTestUser.getUid(), false)).thenReturn(vote);
        when(userResponseBrokerMock.checkValidityOfResponse(vote, msg)).thenReturn(true);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(dummyNotification(sessionTestUser, vote));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userResponseBrokerMock, times(1)).recordUserResponse(sessionTestUser.getUid(), JpaEntityType.VOTE,
                vote.getUid(), "TWO");
        verify(userResponseBrokerMock, times(1)).checkValidityOfResponse(vote, msg);
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
    }


    /**
     * In this case user sent message "four" while there is outstanding vote NOT having this option
     * We are verifying that:
     * <ul>
     * <li> no vote response will be recorded </li>
     * <li> no rsvp will be recorded</li>
     * <li> user log will be recorded </li>
     * <li> recent notifications will be checked </li>
     * <li> no group log will be recorded </li>
     * </ul>
     */
    @Test
    public void invalidResponseToCustomOptionsVote() throws Exception {

        String msg = "four";
        Vote vote = createVote(new String[]{"one", "two", "three"});
        vote.setRsvpRequired(true);

        List<Event> votes = Collections.singletonList(vote);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.getEventsNeedingResponseFromUser(sessionTestUser)).thenReturn(votes);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(dummyNotification(sessionTestUser, vote));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(logsAndNotificationsBrokerMock, times(1)).asyncStoreBundle(any(LogsAndNotificationsBundle.class));
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(groupLogRepositoryMock);
    }


    /**
     * In this case user sent sms but there are no outstanding meetings or votes and there are no notifications sent to this user in last six hours
     * We are verifying that:
     * <ul>
     * <li> no meeting rsvp will be recorded</li>
     * <li> no vote response will be recorded </li>
     * <li> user log WILL be recorded </li>
     * <li> recent notifications will be checked </li>
     * <li> no group log will be recorded </li>
     * </ul>
     */
    @Test
    public void responseArrivedButNoMeetingsOrVotes() throws Exception {

        WelcomeNotification ntf = new WelcomeNotification(sessionTestUser, "hello",
                new UserLog(sessionTestUser.getUid(), UserLogType.INITIATED_USSD, "hello", UserInterfaceType.INCOMING_SMS));

        String msg = "yes";
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(ntf)));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        // checking that we store the weird response (i.e., in this case there was a notification, just not for a meeting or vote
        verify(userResponseBrokerMock).checkForEntityForUserResponse(sessionTestUser.getUid(), false);
        verify(logsAndNotificationsBrokerMock).asyncStoreBundle(any(LogsAndNotificationsBundle.class));

        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(groupLogRepositoryMock);

    }


    /**
     * In this case user sent sms but there are no outstanding meetings or votes and THERE IS a notification sent to this user in last six hours
     * We are verifying that:
     * <ul>
     *  <li> no meeting rsvp will be recorded</li>
     *  <li> no vote response will be recorded </li>
     *  <li> user log WILL be recorded </li>
     *  <li> recent notifications will be checked </li>
     *  <li> group log WILL be recorded </li>
     * </ul>
     */
    @Test
    public void responseArrivedButNoMeetingsOrVotes2() throws Exception {

        meeting = meetingEvent;
        String msg = "some text";
        Notification ntf = new EventInfoNotification(sessionTestUser, "Meeting called",
                new EventLog(sessionTestUser, meeting, EventLogType.CREATED));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(logsAndNotificationsBrokerMock.lastNotificationsSentToUser(eq(sessionTestUser), anyInt(), any(Instant.class)))
                .thenReturn(new PageImpl<>(Collections.singletonList(ntf)));

        mockMvc.perform(get(path + "reply").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(logsAndNotificationsBrokerMock).asyncStoreBundle(any(LogsAndNotificationsBundle.class));
        verifyZeroInteractions(eventLogBrokerMock);
    }


}
