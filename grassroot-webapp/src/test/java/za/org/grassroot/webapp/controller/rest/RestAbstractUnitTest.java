package za.org.grassroot.webapp.controller.rest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.*;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupQueryBroker;
import za.org.grassroot.services.task.*;
import za.org.grassroot.services.user.GcmRegistrationBroker;
import za.org.grassroot.services.user.UserManagementService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static za.org.grassroot.core.util.DateTimeUtil.convertToUserTimeZone;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class RestAbstractUnitTest {
    protected final static String testUserPhone = "27815550000";
    protected final static String testUserCode = "2394";
    protected final static String testGroupName = "test_group";
    protected final static String testSearchTerm = "testGroup";
    protected final static String testEventLocation = "Jozi-Hub";
    protected final static String testEventTitle = "Test_Event";
    protected final static String testEventDescription = "A feedback on code reviews.";
    protected final static Instant testInstant = Instant.now().plus(5, ChronoUnit.HOURS);
    protected final static LocalDateTime testDateTime = convertToUserTimeZone(testInstant, getSAST()).toLocalDateTime();
    protected final static User sessionTestUser = new User(testUserPhone, "testUser", null);
    protected final static Group testGroup = new Group(testGroupName, sessionTestUser);

    protected MockMvc mockMvc;


    protected final static Meeting meetingEvent = new MeetingBuilder().setName(testEventTitle).setStartDateTime(testInstant).setUser(sessionTestUser).setParent(testGroup).setEventLocation(testEventLocation).setIncludeSubGroups(true).setReminderType(EventReminderType.DISABLED).setCustomReminderMinutes(15).setDescription(testEventDescription).setImportance(null).createMeeting();

    protected final static Todo TEST_TO_DO = new Todo(sessionTestUser, testGroup, TodoType.ACTION_REQUIRED, "A test to-do", testInstant);

    @Mock
    protected PermissionBroker permissionBrokerMock;

    @Mock
    protected EventLogBroker eventLogBrokerMock;

    @Mock
    protected EventLogRepository eventLogRepositoryMock;

    @Mock
    protected TodoBroker todoBrokerMock;

    @Mock
    protected UserManagementService userManagementServiceMock;

    @Mock
    protected GroupJoinRequestService groupJoinRequestServiceMock;

    @Mock
    protected GroupBroker groupBrokerMock;

    @Mock
    protected GroupQueryBroker groupQueryBrokerMock;

    @Mock
    protected GroupLogRepository groupLogRepositoryMock;

    @Mock
    protected EventBroker eventBrokerMock;

    @Mock
    protected TaskBroker taskBrokerMock;

    @Mock
    protected GcmRegistrationBroker gcmRegistrationBrokerMock;

    @Mock
    protected UserLogRepository userLogRepositoryMock;


    Vote createVote(String[] options) {
        Vote voteEvent = new Vote(testEventTitle, testInstant, sessionTestUser, testGroup, true, testEventDescription);
        voteEvent.setTags(options);
        return voteEvent;
    }


    @Test
    public void dummyTest () throws Exception {
        //required to the runwith annotation
    }
}
