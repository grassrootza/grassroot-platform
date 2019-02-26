package za.org.grassroot.webapp.controller.rest;

import lombok.extern.slf4j.Slf4j;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.JpaEntityType;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.integration.authentication.JwtService;
import za.org.grassroot.services.task.MeetingBuilderHelper;
import za.org.grassroot.webapp.controller.rest.task.EventCreateController;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Slf4j
public class TaskCreateControllerTest extends RestAbstractUnitTest {

    private static final String path = "/v2/api/task/create";

    private static final List<String> testVoteOptions = new ArrayList<>();

    private static final String testSubject = "The Meeting";
    private static final long testDateTimeEpochMillis = DateTimeUtil.convertToSystemTime(testDateTime,
            DateTimeUtil.getSAST()).toEpochMilli();

    private Vote testVote = createVote(null);
    private static final Instant oneDayAway = Instant.now().plus(1, ChronoUnit.DAYS);

    @Mock
    private JwtService jwtServiceMock;

    @InjectMocks
    private EventCreateController meetingCreateController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(meetingCreateController).build();
        testVoteOptions.add("Option");
    }

    @Test
    public void createMeetingShouldWork() throws Exception{
        Group dummyGroup = new Group("Dummy Group3", GroupPermissionTemplate.DEFAULT_GROUP, new User(
                "234345345", null, null));

        Meeting dummyMeeting = new MeetingBuilder().setName("test meeting")
                .setStartDateTime(oneDayAway)
                .setUser(sessionTestUser)
                .setParent(dummyGroup).setEventLocation("some place").createMeeting();
        MeetingBuilderHelper helper = new MeetingBuilderHelper()
                .userUid(sessionTestUser.getUid())
                .parentType(JpaEntityType.GROUP)
                .parentUid(dummyGroup.getUid())
                .name(testSubject)
                .location(testEventLocation)
                .startDateTimeInstant(Instant.ofEpochMilli(testDateTimeEpochMillis));

        when(jwtServiceMock.getUserIdFromJwtToken("testing")).thenReturn(sessionTestUser.getUid());
        when(eventBrokerMock.createMeeting(helper, UserInterfaceType.REST_GENERIC)).thenReturn(dummyMeeting);
        mockMvc.perform(post(path + "/meeting/{parentType}/{parentUid}",
                JpaEntityType.GROUP,
                dummyGroup.getUid())
                .header("Authorization", "bearer_testing")
                .param("subject", testSubject)
                .param("location", testEventLocation)
                .param("dateTimeEpochMillis", "" + testDateTimeEpochMillis)
        ).andExpect(status().is2xxSuccessful());

        verify(eventBrokerMock,times(1)).createMeeting(helper, UserInterfaceType.REST_GENERIC);
    }
}
