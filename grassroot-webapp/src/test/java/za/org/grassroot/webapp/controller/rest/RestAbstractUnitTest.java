package za.org.grassroot.webapp.controller.rest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.repository.EventLogRepository;
import za.org.grassroot.core.repository.VerificationTokenCodeRepository;
import za.org.grassroot.integration.services.GcmService;
import za.org.grassroot.integration.sms.SmsSendingService;
import za.org.grassroot.services.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static za.org.grassroot.core.util.DateTimeUtil.convertToUserTimeZone;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;

/**
 * Created by paballo on 2016/02/18.
 *
 *
 */
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
    protected final static User sessionTestUser = new User(testUserPhone, "testUser");

    protected final static Group testGroup = new Group(testGroupName, sessionTestUser);

    protected MockMvc mockMvc;

    protected final static Vote voteEvent = new Vote(testEventTitle,
                                                     testInstant,
                                                     sessionTestUser,
                                                     testGroup,
                                                     true,
                                                     true,
                                                     testEventDescription);

    protected final static Meeting meetingEvent = new Meeting(testEventTitle, testInstant, sessionTestUser, testGroup, testEventLocation, true, true, true, EventReminderType.DISABLED, 15, testEventDescription);

    protected final static Todo TEST_TO_DO = new Todo(sessionTestUser, testGroup, "A test log book", testInstant);


    @Mock
    protected PermissionBroker permissionBrokerMock;
    @Mock
    protected AccountBroker accountBrokerMock;
    @Mock
    protected EventLogBroker eventLogBrokerMock;
    @Mock
    protected EventLogRepository eventLogRepositoryMock;
    @Mock
    protected TodoBroker todoBrokerMock;
    @Mock
    protected UserManagementService userManagementServiceMock;
    @Mock
    protected PasswordTokenService passwordTokenServiceMock;
    @Mock
    protected GroupJoinRequestService groupJoinRequestServiceMock;
    @Mock
    protected VerificationTokenCodeRepository verificationTokenCodeRepositoryMock;
    @Mock
    protected GroupBroker groupBrokerMock;
    @Mock
    protected GroupQueryBroker groupQueryBrokerMock;

    @Mock
    protected EventBroker eventBrokerMock;
    @Mock
    protected TaskBroker taskBrokerMock;
    @Mock
    protected GcmService gcmServiceMock;
    @Mock
    protected MessageAssemblingService messageAssemblingServiceMock;
    @Mock
    protected SmsSendingService smsSendingServiceMock;

    protected MessageSource messageSource() {

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }

    @Test
    public void dummyTest() throws Exception{

    }

}
