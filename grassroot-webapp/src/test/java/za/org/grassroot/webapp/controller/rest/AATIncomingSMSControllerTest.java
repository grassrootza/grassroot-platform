package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;

import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by paballo on 2016/02/18.
 */
public class AATIncomingSMSControllerTest extends RestAbstractUnitTest {

    private static final String path = "/sms/";

    @InjectMocks
    AATIncomingSMSController aatIncomingSMSController;


    @Before
    public void setUp() {
        mockMvc =   MockMvcBuilders.standaloneSetup(aatIncomingSMSController).build();

    }

    @Test
    public void receiveSMSShouldWorkWithValidInput() throws Exception{

        sessionTestUser.setId(1L);
        Event meeting = new Event();
        meeting.setEventType(EventType.Meeting);
        meeting.setRsvpRequired(true);
        List<Event> meetings = Arrays.asList(meeting);
        EventLog log = new EventLog();
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(userManagementServiceMock.needsToRSVP(sessionTestUser)).thenReturn(true);
        when(userManagementServiceMock.needsToVote(sessionTestUser)).thenReturn(false);
        when(eventManagementServiceMock.getOutstandingRSVPForUser(sessionTestUser)).thenReturn(meetings);
        when(eventLogManagementServiceMock.rsvpForEvent(meeting.getId(),sessionTestUser.getId(),
                EventRSVPResponse.fromString("yes"))).thenReturn(log);
        mockMvc.perform(get(path+"incoming").param("FN",testUserPhone).param("MS", "yes"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).loadOrSaveUser(testUserPhone);
        verify(userManagementServiceMock,times(1)).needsToRSVP(sessionTestUser);
        verify(userManagementServiceMock,times(1)).needsToVote(sessionTestUser);
        verify(eventManagementServiceMock,times(1)).getOutstandingRSVPForUser(sessionTestUser);
        verify(eventLogManagementServiceMock,times(1)).rsvpForEvent(meeting.getId(),sessionTestUser.getId(),
                EventRSVPResponse.fromString("yes"));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);

    }

    @Test
    public void receiveSMSShouldWorkWithInvalidInput() throws Exception{

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.notifyUnableToProcessEventReply(sessionTestUser)).thenReturn(0);
        mockMvc.perform(get(path+"incoming").param("FN",testUserPhone).param("MS", "yebo"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).loadOrSaveUser(testUserPhone);
        verify(eventManagementServiceMock, times(1)).notifyUnableToProcessEventReply(sessionTestUser);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }


}
