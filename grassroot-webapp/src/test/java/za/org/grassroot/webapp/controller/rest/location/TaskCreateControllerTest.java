package za.org.grassroot.webapp.controller.rest.location;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;
import za.org.grassroot.webapp.controller.rest.task.EventCreateController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

public class TaskCreateControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(TaskCreateControllerTest.class);

    private static final String path = "/api/task/create";

    private static final List<String> testVoteOptions = new ArrayList<>();

    private static final String testSubject = "The Meeting";
    private static final long testDateTimeEpochMillis = 000000000000;

    private Vote testVote = createVote(null);
    private static final Instant oneDayAway = Instant.now().plus(1, ChronoUnit.DAYS);

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

        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(eventBrokerMock.createMeeting(helper)).thenReturn(dummyMeeting);
        mockMvc.perform(post(path + "/meeting/{userUid}/{parentType}/{parentUid}",
                sessionTestUser.getUid(),
                JpaEntityType.GROUP,
                dummyGroup.getUid())
                .param("subject", testSubject)
                .param("location", testEventLocation)
                .param("dateTimeEpochMillis", "" + testDateTimeEpochMillis)
        ).andExpect(status().is2xxSuccessful());

        verify(eventBrokerMock,times(1)).createMeeting(helper);
    }


    @Test
    public void createVoteShouldWork() throws Exception{
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);

        when(eventBrokerMock.createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP,
                testEventTitle, testDateTime, false, testEventDescription,
                Collections.emptySet(), null)).thenReturn(testVote);

        mockMvc.perform(post(path + "/vote/{userUid}/{parentType}/{parentUid}",sessionTestUser.getUid(),JpaEntityType.GROUP,testGroup.getUid())
                .param("description",""+testEventDescription)
                .param("time",testDateTime.format(getPreferredRestFormat()))
                .param("title",""+ testEventTitle)
                ).andExpect(status().is2xxSuccessful());

        verify(userManagementServiceMock,times(1)).load(sessionTestUser.getUid());
        verify(eventBrokerMock,times(1)).createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP, testVote.getName(),
                testDateTime, false, testEventDescription, Collections.emptySet(), null);
    }

    Vote createVote(String[] options) {
        Vote voteEvent = new Vote(testEventTitle, testInstant, sessionTestUser, testGroup, true, testEventDescription);
        voteEvent.setTags(options);
        return voteEvent;
    }
}
