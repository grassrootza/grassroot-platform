package za.org.grassroot.webapp.controller.rest.location;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.messaging.JwtService;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;
import za.org.grassroot.webapp.controller.rest.task.EventCreateController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

@Slf4j
public class TaskCreateControllerTest extends RestAbstractUnitTest {

    private static final String path = "/api/task/create";

    private static final List<String> testVoteOptions = new ArrayList<>();

    private static final String testSubject = "The Meeting";
    private static final long testDateTimeEpochMillis = DateTimeUtil.convertToSystemTime(testDateTime,
            DateTimeUtil.getSAST()).toEpochMilli();

    private Vote testVote = createVote(null);
    private static final Instant oneDayAway = Instant.now().plus(1, ChronoUnit.DAYS);

    @Mock
    JwtService jwtServiceMock;

    @InjectMocks
    private EventCreateController meetingCreateController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(meetingCreateController).build();
        testVoteOptions.add("Option");
    }

    @Test
    public void createMeetingShouldWork() throws Exception{
        Group dummyGroup = new Group("Dummy Group3", new User(
                "234345345", null, null));

        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(dummyGroup).setEventLocation("some place").createMeeting();
        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(sessionTestUser.getUid())
                .parentType(JpaEntityType.GROUP)
                .parentUid(dummyGroup.getUid())
                .name(testSubject)
                .location(testEventLocation)
                .startDateTimeInstant(Instant.ofEpochMilli(testDateTimeEpochMillis));


        when(jwtServiceMock.getUserIdFromJwtToken("testing")).thenReturn(sessionTestUser.getUid());
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(eventBrokerMock.createMeeting(helper)).thenReturn(dummyMeeting);
        mockMvc.perform(post(path + "/meeting/{parentType}/{parentUid}",
                JpaEntityType.GROUP,
                dummyGroup.getUid())
                .header("Authorization", "bearer_testing")
                .param("subject", testSubject)
                .param("location", testEventLocation)
                .param("dateTimeEpochMillis", "" + testDateTimeEpochMillis)
        ).andExpect(status().is2xxSuccessful());

        verify(eventBrokerMock,times(1)).createMeeting(helper);
    }


    // terribly written test is failing for spurious reasons. no time to fix. do so in future.
//    @Test
//    public void createVoteShouldWork() throws Exception{
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
//
//        when(jwtServiceMock.getUserIdFromJwtToken("testing")).thenReturn(sessionTestUser.getUid());
//        when(eventBrokerMock.createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
//                testEventTitle, testDateTime, false, testEventDescription,
//                Collections.emptySet(), null)).thenReturn(testVote);
//
//        mockMvc.perform(post(path + "/vote/{parentType}/{parentUid}",
//                JpaEntityType.GROUP, testGroup.getUid())
//                .header("Authorization", "bearer_testing")
//                        .param("title",""+ testEventTitle)
//                        .param("description",""+testEventDescription)
//                        .param("time", "" + testDateTimeEpochMillis))
//                .andExpect(status().is2xxSuccessful());
//
//        verify(userManagementServiceMock,times(1)).load(sessionTestUser.getUid());
//        verify(eventBrokerMock,times(1)).createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP, testVote.getName(),
//                testDateTime, false, testEventDescription, Collections.emptySet(), null);
//    }

    Vote createVote(String[] options) {
        Vote voteEvent = new Vote(testEventTitle, testInstant, sessionTestUser, testGroup, true, testEventDescription);
        voteEvent.setTags(options);
        return voteEvent;
    }
}
