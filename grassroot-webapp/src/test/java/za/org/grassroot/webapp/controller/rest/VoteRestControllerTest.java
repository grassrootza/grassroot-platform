package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.EventReminderType;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
public class VoteRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    VoteRestController voteRestController;

    String path = "/api/vote";

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(voteRestController).build();
    }

    @Test
    public void creatingAVoteShouldWork() throws Exception {

     /*   when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.createVote(sessionTestUser.getUid(), voteEvent.getUid(), voteEvent.getName(), testTimestamp, true, true, testEventDescription, Collections.emptySet())).thenReturn(voteEvent);
        Mockito.when(eventBrokerMock.updateReminderSettings(sessionTestUser.getUid(),voteEvent.getUid(),EventReminderType.CUSTOM,0);
        when(eventBrokerMock.updateReminderSettings(sessionTestUser.getUid(),voteEvent.getUid(),EventReminderType.CUSTOM,0)).thenReturn(Void void)
        mockMvc.perform(post(path + "/create/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode).param("title", testEventTitle).param("closingTime", String.valueOf(testTimestamp)).param("description", testEventDescription).param("reminderMins", String.valueOf(0)).param("notifyGroup", String.valueOf(true)).param("includeSubgroups", String.valueOf(true))).andExpect(status().isCreated()).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).createVote(sessionTestUser.getUid(), voteEvent.getUid(), voteEvent.getName(), testTimestamp, true, true, testEventDescription, Collections.emptySet());*/
    }

    @Test
    public void viewingAVoteShouldWork() throws Exception {

        group.addMember(sessionTestUser, new Role("ROLE_GROUP_ORGANIZER", group.getUid()));
        EventLog eventLog = new EventLog(sessionTestUser, voteEvent, EventLogType.EventRSVP, "This is a test log");
        List<Object[]> list = new ArrayList<>();
        String[] responses = {"1", "2", "3", "4", "5"};
        list.add(responses);
        RSVPTotalsDTO rsvpTotalsDTO = new RSVPTotalsDTO(list);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogManagementServiceMock.getEventLogOfUser(voteEvent, sessionTestUser, EventLogType.EventRSVP)).thenReturn(eventLog);
        when(eventLogManagementServiceMock.userRsvpForEvent(voteEvent, sessionTestUser)).thenReturn(true);
        when(eventLogManagementServiceMock.getVoteResultsForEvent(voteEvent)).thenReturn(rsvpTotalsDTO);
        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).load(voteEvent.getUid());
        verify(eventLogManagementServiceMock).getEventLogOfUser(voteEvent, sessionTestUser, EventLogType.EventRSVP);
        verify(eventLogManagementServiceMock).userRsvpForEvent(voteEvent, sessionTestUser);
        verify(eventLogManagementServiceMock).getVoteResultsForEvent(voteEvent);
    }

    @Test
    public void castingVotesShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogManagementServiceMock.userRsvpForEvent(voteEvent, sessionTestUser)).thenReturn(false);
        mockMvc.perform(get(path + "/do/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode).param("response", "Yes")).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).load(voteEvent.getUid());
        verify(eventLogManagementServiceMock).userRsvpForEvent(voteEvent, sessionTestUser);
    }

    @Test
    public void updatingTheVoteShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testTimestamp, testEventDescription )).thenReturn(voteEvent);
        mockMvc.perform(post(path + "/update/{id}/{phoneNumber}/{code}",  voteEvent.getUid(), testUserPhone,  testUserCode).param("title", "Test_Vote").param("closingTime", String.valueOf(testTimestamp)).param("description", testEventDescription)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testTimestamp, testEventDescription);
    }
}
