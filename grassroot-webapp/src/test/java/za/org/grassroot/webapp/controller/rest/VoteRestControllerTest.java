package za.org.grassroot.webapp.controller.rest;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.Role;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.rest.android.VoteRestController;

import java.util.Collections;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
public class VoteRestControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(VoteRestControllerTest.class);

    private static final String path = "/api/vote";

    @Mock
    private VoteBroker voteBrokerMock;

    @InjectMocks
    private VoteRestController voteRestController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(voteRestController).build();
    }

    @Test
    public void creatingAVoteShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);

        when(eventBrokerMock.createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                                        voteEvent.getName(), testDateTime, false, testEventDescription,
                                        Collections.emptySet(), null)).thenReturn(voteEvent);

        log.info("ZOG: Creating a vote, passing these parameters: userUid= {}, groupUid= {}, voteName= {}, time= {}",
                 sessionTestUser.getUid(), testGroup.getUid(), testEventTitle, testDateTime.toString());

        mockMvc.perform(post(path + "/create/{id}/{phoneNumber}/{code}", testGroup.getUid(), testUserPhone, testUserCode)
                                .param("title", testEventTitle)
                                .param("closingTime", testDateTime.format(getPreferredRestFormat()))
                                .param("description", testEventDescription)
                                .param("reminderMins", String.valueOf(10))
                                .param("notifyGroup", String.valueOf(true))
                                .param("includeSubgroups", String.valueOf(true)))
                .andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP, voteEvent.getName(),
                                           testDateTime, false, testEventDescription, Collections.emptySet(), null);
    }

    @Test
    public void viewingAVoteShouldWork() throws Exception {

        testGroup.addMember(sessionTestUser, new Role("ROLE_GROUP_ORGANIZER", testGroup.getUid()));
        EventLog eventLog = new EventLog(sessionTestUser, voteEvent, EventLogType.RSVP, EventRSVPResponse.YES);
        ResponseTotalsDTO rsvpTotalsDTO = ResponseTotalsDTO.makeForTest(1, 2, 3, 4, 5);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(voteBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogRepositoryMock.findOne(any(Specifications.class))).thenReturn(eventLog);
        when(eventLogBrokerMock.hasUserRespondedToEvent(voteEvent, sessionTestUser)).thenReturn(true);
        when(eventLogBrokerMock.getResponseCountForEvent(voteEvent)).thenReturn(rsvpTotalsDTO);

        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(voteBrokerMock).load(voteEvent.getUid());
        verify(voteBrokerMock).fetchVoteResults(sessionTestUser.getUid(), voteEvent.getUid());
        verify(eventLogRepositoryMock).findOne(any(Specifications.class));
    }

    @Test
    public void castingVotesShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogBrokerMock.hasUserRespondedToEvent(voteEvent, sessionTestUser)).thenReturn(false);
        mockMvc.perform(get(path + "/do/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode)
                .param("response", "YES"))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).load(voteEvent.getUid());
        verify(voteBrokerMock).recordUserVote(sessionTestUser.getUid(), voteEvent.getUid(), "YES");
    }

    @Test
    public void updatingTheVoteShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testDateTime, testEventDescription)).thenReturn(voteEvent);
        mockMvc.perform(post(path + "/update/{id}/{phoneNumber}/{code}",  voteEvent.getUid(), testUserPhone,  testUserCode)
                                .param("title", "Test_Vote")
                                .param("closingTime", testDateTime.format(getPreferredRestFormat()))
                                .param("description", testEventDescription))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testDateTime, testEventDescription);
    }
}
