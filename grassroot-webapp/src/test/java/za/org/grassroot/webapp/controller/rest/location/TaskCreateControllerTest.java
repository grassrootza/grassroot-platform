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
import za.org.grassroot.webapp.controller.rest.RestAbstractUnitTest;
import za.org.grassroot.webapp.controller.rest.task.TaskCreateController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.getPreferredRestFormat;

public class TaskCreateControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(TaskCreateControllerTest.class);

    private static final String path = "/api/task/create";

    private static final List<String> testVoteOptions = new ArrayList<>();

    private static final String testSubject = "The Meetin";
    private static final long testDateTimeEpochMillis = 000000000000;

    Vote testVote = createVote(null);
    private static final Instant oneDayAway = Instant.now().plus(1, ChronoUnit.DAYS);

    @InjectMocks
    private TaskCreateController taskCreateController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(taskCreateController).build();
        testVoteOptions.add("Option");
    }

    @Test
    public void createMeetingShouldWork() throws Exception{
        Group dummyGroup = new Group("Dummy Group3", new User(
                "234345345"));
        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting").setStartDateTime(oneDayAway).setUser(sessionTestUser).setParent(dummyGroup).setEventLocation("some place").createMeeting();

        when(eventBrokerMock.loadMeeting(dummyMeeting.getUid())).thenReturn(dummyMeeting);
        mockMvc.perform(post(path + "/meeting/{userUid}/{parentType}/{parentUid}",sessionTestUser.getUid(),JpaEntityType.MEETING,dummyGroup.getUid())
                .param("subject",""+testSubject)
                .param("location",""+testEventLocation)
                .param("dateTimeEpochMillis",""+testDateTimeEpochMillis)
        ).andExpect(status().is2xxSuccessful());
    }


    @Test
    public void createVoteShouldWork() throws Exception{
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        when(eventBrokerMock.createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.VOTE,
                testEventTitle, testDateTime, false, testEventDescription,
                Collections.emptySet(), null)).thenReturn(testVote);

        mockMvc.perform(post(path + "/vote/{userUid}/{parentUid}",sessionTestUser.getUid(),testGroup.getUid())
                .param("description",""+testEventDescription)
                .param("time",testDateTime.format(getPreferredRestFormat()))
                .param("title",""+ testEventTitle)
                ).andExpect(status().is2xxSuccessful());
    }

    Vote createVote(String[] options) {
        Vote voteEvent = new Vote(testEventTitle, testInstant, sessionTestUser, testGroup, true, testEventDescription);
        voteEvent.setTags(options);
        return voteEvent;
    }
}
