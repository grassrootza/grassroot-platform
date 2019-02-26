package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.task.EventLog;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.dto.ResponseTotalsDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.services.task.VoteHelper;
import za.org.grassroot.webapp.controller.android1.VoteRestController;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

/**
 * Created by Siyanda Mzam on 2016/03/22.
 */
@Slf4j
public class VoteRestControllerTest extends RestAbstractUnitTest {

    private static final String path = "/api/vote";

    private Vote voteEvent = createVote(null);

    @Mock private VoteBroker voteBrokerMock;

    @InjectMocks private VoteRestController voteRestController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(voteRestController).build();
    }

    @Test
    public void creatingAVoteShouldWork() throws Exception {
        VoteHelper helper = VoteHelper.builder()
                .userUid(sessionTestUser.getUid()).parentUid(testGroup.getUid())
                .name(voteEvent.getName()).eventStartDateTime(testDateTime).description(testEventDescription)
                .build();

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.createVote(helper)).thenReturn(voteEvent);

        log.info("HELPER: {}", helper);

        mockMvc.perform(post(path + "/create/{id}/{phoneNumber}/{code}", testGroup.getUid(), testUserPhone, testUserCode)
                                .param("title", testEventTitle)
                                .param("closingTime", testDateTime.format(getPreferredRestFormat()))
                                .param("description", testEventDescription)
                                .param("reminderMins", String.valueOf(10))
                                .param("notifyGroup", String.valueOf(true)))
                .andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(eventBrokerMock).createVote(helper);
    }

    @Test
    public void viewingAVoteShouldWork() throws Exception {

        testGroup.addMember(sessionTestUser, GroupRole.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        EventLog eventLog = new EventLog(sessionTestUser, voteEvent, EventLogType.RSVP, EventRSVPResponse.YES);
        ResponseTotalsDTO rsvpTotalsDTO = ResponseTotalsDTO.makeForTest(1, 2, 3, 4, 5);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(voteBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
        when(eventLogRepositoryMock.findOne(any(Specification.class))).thenReturn(Optional.of(eventLog));

        mockMvc.perform(get(path + "/view/{id}/{phoneNumber}/{code}", voteEvent.getUid(), testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(voteBrokerMock).load(voteEvent.getUid());
        verify(voteBrokerMock).fetchVoteResults(sessionTestUser.getUid(), voteEvent.getUid(), false);
        verify(eventLogRepositoryMock).findOne(any(Specification.class));
    }

    @Test
    public void castingVotesShouldWork() throws Exception {

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(eventBrokerMock.load(voteEvent.getUid())).thenReturn(voteEvent);
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
        when(voteBrokerMock.updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testDateTime, testEventDescription)).thenReturn(voteEvent);
        mockMvc.perform(post(path + "/update/{id}/{phoneNumber}/{code}",  voteEvent.getUid(), testUserPhone,  testUserCode)
                                .param("title", "Test_Vote")
                                .param("closingTime", testDateTime.format(getPreferredRestFormat()))
                                .param("description", testEventDescription))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(voteBrokerMock).updateVote(sessionTestUser.getUid(), voteEvent.getUid(), testDateTime, testEventDescription);
    }
}
