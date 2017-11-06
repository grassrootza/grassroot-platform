package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.integration.NotificationService;
import za.org.grassroot.integration.messaging.MessagingServiceBroker;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.rest.incoming.IncomingSMSController;

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
    private NotificationService notificationServiceMock;

    @Mock
    private VoteBroker voteBrokerMock;

    @InjectMocks
    private IncomingSMSController aatIncomingSMSController;


    @Before
    public void setUp() {
        mockMvc =   MockMvcBuilders.standaloneSetup(aatIncomingSMSController).build();
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
        List<Event> meetings = Collections.singletonList(meeting);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.getOutstandingResponseForUser(sessionTestUser, EventType.MEETING)).thenReturn(meetings);

        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", "yes"))
                .andExpect(status().isOk());

        verify(eventLogBrokerMock, times(1)).rsvpForEvent(meeting.getUid(), sessionTestUser.getUid(), EventRSVPResponse.YES);
        verifyZeroInteractions(voteBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
        verifyZeroInteractions(notificationServiceMock);
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
        List<Event> meetings = Collections.singletonList(meeting);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.getOutstandingResponseForUser(sessionTestUser, EventType.MEETING)).thenReturn(meetings);


        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userLogRepositoryMock, times(1)).save(any(UserLog.class));
        verify(notificationServiceMock, times(1)).fetchSentOrBetterSince(anyString(), anyObject(), eq(null));
        verifyZeroInteractions(voteBrokerMock);
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(groupLogRepositoryMock);
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
        when(eventBrokerMock.getOutstandingResponseForUser(sessionTestUser, EventType.VOTE)).thenReturn(votes);

        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(voteBrokerMock, times(1)).recordUserVote(sessionTestUser.getUid(), vote.getUid(), msg);
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
        verifyZeroInteractions(notificationServiceMock);
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
        List<Event> votes = Collections.singletonList(vote);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.getOutstandingResponseForUser(sessionTestUser, EventType.VOTE)).thenReturn(votes);

        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(voteBrokerMock, times(1)).recordUserVote(sessionTestUser.getUid(), vote.getUid(), "two");
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(userLogRepositoryMock);
        verifyZeroInteractions(groupLogRepositoryMock);
        verifyZeroInteractions(notificationServiceMock);
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
        when(eventBrokerMock.getOutstandingResponseForUser(sessionTestUser, EventType.VOTE)).thenReturn(votes);

        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userLogRepositoryMock, times(1)).save(any(UserLog.class));
        verify(notificationServiceMock, times(1)).fetchSentOrBetterSince(anyString(), anyObject(), eq(null));
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(voteBrokerMock);
        verifyZeroInteractions(groupLogRepositoryMock);
    }


    /**
     * In this case user sent sms but there are no outstanding meetings or votes and there are no notifications sent to this user in last six hours
     * We are verifying that:
     * <ul>
     * <li> no meeting rsvp will be recorded</li>
     * <li> no vote response will be recorded </li>
     * <li> SMS with error report WILL be sent back to user </li>
     * <li> user log WILL be recorded </li>
     * <li> recent notifications will be checked </li>
     * <li> no group log will be recorded </li>
     * </ul>
     */
    @Test
    public void responseArrivedButNoMeetingsOrVotes() throws Exception {

        String msg = "yes";
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userLogRepositoryMock).save(any(UserLog.class));
        verify(messagingServiceBroker).sendSMS(anyString(), anyString(), anyBoolean());
        verify(notificationServiceMock).fetchSentOrBetterSince(anyString(), anyObject(), eq(null));

        verifyZeroInteractions(voteBrokerMock);
        verifyZeroInteractions(eventLogBrokerMock);
        verifyZeroInteractions(groupLogRepositoryMock);

    }


    /**
     * In this case user sent sms but there are no outstanding meetings or votes and THERE IS a notification sent to this user in last six hours
     * We are verifying that:
     * <ul>
     *  <li> no meeting rsvp will be recorded</li>
     *  <li> no vote response will be recorded </li>
     *  <li> SMS with error report WILL be sent back to user </li>
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
        when(notificationServiceMock.fetchSentOrBetterSince(anyString(), anyObject(), eq(null))).thenReturn(Collections.singletonList(ntf));

        mockMvc.perform(get(path + "incoming").param("fn", testUserPhone).param("ms", msg))
                .andExpect(status().isOk());

        verify(userLogRepositoryMock).save(any(UserLog.class));
        verify(messagingServiceBroker).sendSMS(anyString(), anyString(), anyBoolean());
        verify(notificationServiceMock).fetchSentOrBetterSince(anyString(), anyObject(), eq(null));
        verify(groupLogRepositoryMock).save(any(GroupLog.class));

        verifyZeroInteractions(voteBrokerMock);
        verifyZeroInteractions(eventLogBrokerMock);

    }


}
