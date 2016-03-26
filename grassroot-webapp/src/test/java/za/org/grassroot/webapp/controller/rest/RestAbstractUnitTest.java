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
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.*;

import java.sql.Timestamp;

/**
 * Created by paballo on 2016/02/18.
 *
 *
 */
@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class RestAbstractUnitTest
{
    protected final static String testUserPhone = "27815550000";
    protected final static String testUserCode = "2394";
    protected final static String testGroupName = "test_group";
    protected final static String testSearchTerm = "group";
    protected final static String testEventLocation = "Jozi-Hub";
    protected final static String testEventTitle = "Test_Event";
    protected final static String testEventDescription = "A feedback on code reviews.";
    protected final static Timestamp testTimestamp = Timestamp.valueOf(DateTimeUtil.parseDateTime((DateTimeUtil.addHoursFromNow(5)).toString()));
    protected final static User sessionTestUser = new User(testUserPhone, "testUser");
    protected final static Group group = new Group(testGroupName, sessionTestUser);
    protected final static Vote voteEvent = new Vote(testEventTitle, testTimestamp, sessionTestUser, group, true, true, testEventDescription);
    protected final static Meeting meetingEvent = new Meeting(testEventTitle, testTimestamp, sessionTestUser, group, testEventLocation, true, true, true, EventReminderType.DISABLED, 15, testEventDescription);
    protected final static LogBook testLogBook = new LogBook(sessionTestUser, group, "A test log book", testTimestamp);
    protected MockMvc mockMvc;

    @Mock
    protected PermissionBroker permissionBrokerMock;
    @Mock
    protected AccountManagementService accountManagementServiceMock;
    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;
    @Mock
    protected LogBookService logBookServiceMock;
    @Mock
    protected UserManagementService userManagementServiceMock;
    @Mock
    protected EventManagementService eventManagementServiceMock;
    @Mock
    protected PasswordTokenService passwordTokenServiceMock;
    @Mock
    protected GroupManagementService groupManagementServiceMock;
    @Mock
    protected GroupJoinRequestService groupJoinRequestServiceMock;
    @Mock
    protected GroupLogService groupLogServiceMock;
    @Mock
    protected VerificationTokenCodeRepository verificationTokenCodeRepositoryMock;
    @Mock
    protected GroupBroker groupBrokerMock;
    @Mock
    protected EventBroker eventBrokerMock;

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
