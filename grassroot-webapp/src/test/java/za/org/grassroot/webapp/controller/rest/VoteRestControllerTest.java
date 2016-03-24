package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

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

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.createVote(sessionTestUser.getUid(), voteEvent.getUid(), voteEvent.getName(), testTimestamp, true, true, testEventDescription)).thenReturn(voteEvent);
        mockMvc.perform(post(path + "/create/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode).param("title", "Test_Vote").param("closingTime", String.valueOf(testTimestamp)).param("description", testEventDescription).param("reminderMins", String.valueOf(10)).param("notifyGroup", String.valueOf(true)).param("includeSubgroups", String.valueOf(true))).andExpect(status().isCreated()).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventBrokerMock).createVote(sessionTestUser.getUid(), voteEvent.getUid(), voteEvent.getName(), testTimestamp, true, true, testEventDescription);
    }

    @Test
    public void viewingAVoteShouldWork() throws Exception {

        group.addMember(sessionTestUser, new Role("ROLE_GROUP_ORGANIZER", group.getUid()));
        EventLog eventLog = new EventLog(sessionTestUser, voteEvent, EventLogType.EventRSVP, "This is a test log");
        List<Object[]> list = new ArrayList<>();
        String[] responses = {"1", "2", "3", "4", "5"};
        list.add(responses);
        Map<String, Integer> mappedTotals = new HashMap<>();
        RSVPTotalsDTO rsvpTotalsDTO = new RSVPTotalsDTO(list);
        mappedTotals.put("yes", rsvpTotalsDTO.getYes());
        mappedTotals.put("no", rsvpTotalsDTO.getNo());
        mappedTotals.put("maybe", rsvpTotalsDTO.getMaybe());

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
        voteEvent.setId(7385L);
        voteEvent.setEventStartDateTime(testTimestamp);
        voteEvent.setDescription(testEventDescription);
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogManagementServiceMock.userRsvpForEvent(voteEvent, sessionTestUser)).thenReturn(false);
        Logger logger = Logger.getLogger(getClass().getCanonicalName());
        logger.info("The UID of this user is " + voteEvent.getUid());
        mockMvc.perform(get(path + "/do/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode).param("response", "Yes")).andExpect(status().is4xxClientError());
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
