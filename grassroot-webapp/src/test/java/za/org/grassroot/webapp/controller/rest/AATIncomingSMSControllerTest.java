package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by paballo on 2016/02/18.
 */
public class AATIncomingSMSControllerTest extends RestAbstractUnitTest {

    private static final String path = "/sms/";
    Event meeting;

    @InjectMocks
    AATIncomingSMSController aatIncomingSMSController;


    @Before
    public void setUp() {
        mockMvc =   MockMvcBuilders.standaloneSetup(aatIncomingSMSController).build();
    }

    @Test
    public void receiveSMSShouldWorkWithValidInput() throws Exception{

        sessionTestUser.setId(1L);
        meeting = meetingEvent;
        meeting.setId(2L);
        meeting.setRsvpRequired(true);
        List<Event> meetings = Collections.singletonList(meeting);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.notifyUnableToProcessEventReply(sessionTestUser)).thenReturn(1);
        when(userManagementServiceMock.needsToVote(sessionTestUser)).thenReturn(false);
        when(userManagementServiceMock.needsToRSVP(sessionTestUser)).thenReturn(true);
        when(eventManagementServiceMock.getOutstandingVotesForUser(sessionTestUser)).thenReturn(Collections.singletonList(meeting));
        when(eventManagementServiceMock.getOutstandingRSVPForUser(sessionTestUser)).thenReturn(meetings);
        mockMvc.perform(get(path+"incoming").param("fn", testUserPhone).param("ms", "yes"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(userManagementServiceMock).needsToVote(sessionTestUser);
        verify(userManagementServiceMock).needsToRSVP(sessionTestUser);
        verify(eventManagementServiceMock).getOutstandingRSVPForUser(sessionTestUser);

    }

    @Test
    public void receiveSMSShouldWorkWithInvalidInput() throws Exception{

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.notifyUnableToProcessEventReply(sessionTestUser)).thenReturn(0);
        mockMvc.perform(get(path+"incoming").param("fn",testUserPhone).param("ms", "yebo"))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).loadOrSaveUser(testUserPhone);
        verify(eventManagementServiceMock, times(1)).notifyUnableToProcessEventReply(sessionTestUser);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }


}
