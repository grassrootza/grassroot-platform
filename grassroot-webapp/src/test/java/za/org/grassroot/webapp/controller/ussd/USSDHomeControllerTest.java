package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.domain.task.MeetingBuilder;
import za.org.grassroot.services.UserResponseBroker;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.ussd.menus.USSDMenu;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/11/20.
 */
public class USSDHomeControllerTest extends USSDAbstractUnitTest {

    // private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

    private static final String phoneForTests = "27810001111";
    private static final String testUserName = "Test User";
    private static final String testGroupName = "Test Group";

    private static final String openingMenu = "/ussd/start";
    private static final String phoneParameter = "msisdn";

    private User testUser = new User(phoneForTests, testUserName, null);

    @Mock private VoteBroker voteBrokerMock;
    @Mock private UserResponseBroker userResponseBrokerMock;

    // these should be tested in their own controllers, home's role is just to direct
    @Mock private USSDLiveWireController liveWireControllerMock;
    @Mock private USSDGroupController groupControllerMock;
    @Mock private USSDMeetingController meetingControllerMock;

    @InjectMocks
    private USSDHomeController ussdHomeController;

    @InjectMocks
    private USSDVoteController voteController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdHomeController, voteController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();

        wireUpHomeController(ussdHomeController);
        wireUpMessageSourceAndGroupUtil(voteController);

    }

    @Test
    public void testQuestion() throws Exception {
        mockMvc.perform(get("/ussd/test_question")).andExpect(status().isOk());
    }

    @Test
    @Rollback
    public void welcomeMenuShouldWork() throws Exception {
        testUser.setHasInitiatedSession(false);

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(phoneForTests);
    }

    @Test
    public void welcomeMenuAfterChoosingLanguageShouldWork() throws Exception {
        testUser.setHasInitiatedSession(true);
        testUser.setLanguageCode("zu");
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
    }

    @Test
    public void welcomeMenuLocaleShouldWorkWithNoName() throws Exception {

        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
        testUser.setLanguageCode("ts");
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).
                andExpect(status().isOk());

        // no longer done here -- check via language menu
        // verify(userManagementServiceMock, times(1)).updateUserLanguage(testUser.getUid(), new Locale("ts"));
        testUser.setDisplayName(testUserName);
    }

    @Test
    public void forcedStartMenuShouldWork() throws Exception {

        testUser.setHasInitiatedSession(true);
        // switch the next thing to cache manager
        // testUser.setLastUssdMenu("/ussd/mtg/start"); // irrelevant if this is well formed or not, just testing if it asks

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
        mockMvc.perform(get(openingMenu + "_force").param(phoneParameter, phoneForTests)).andExpect(status().isOk());

    }

    @Test
    public void groupJoinTokenShouldWorkInAllLanguages() throws Exception {

        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);

        Group testGroup = new Group(testGroupName, new User("27601110000", null, null));

        testGroup.setGroupTokenCode("111");
        testGroup.setTokenExpiryDateTime(Instant.now().plus(7, ChronoUnit.DAYS));

        // todo : write a test in group controller too
        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(groupControllerMock.lookForJoinCode(testUser, "111")).thenReturn(new USSDMenu("Found the code"));

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests).param("request", "*134*1994*111#"))
                .andExpect(status().isOk());
    }

    @Test
    public void shouldAssembleLiveWire() throws Exception {
        Group group = new Group(testGroupName, testUser);
        Meeting meeting = new MeetingBuilder().setName("").setStartDateTime(Instant.now().plus(1, ChronoUnit.HOURS)).setUser(testUser).setParent(group).setEventLocation("").createMeeting();

        List<Meeting> newMeeting  = Collections.singletonList(meeting);

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(liveWireControllerMock.assembleLiveWireOpening(testUser, 0)).thenReturn(new USSDMenu("LiveWire Menu"));

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests).param("request", "*134*1994*411#")).
                andExpect(status().isOk());

        verify(userManagementServiceMock,times(1)).loadOrCreateUser(phoneForTests);
        verify(liveWireControllerMock, times(1)).assembleLiveWireOpening(testUser, 0);

    }

    /*
    Make sure that exit page works properly
     */
    @Test
    public void errorPagesShouldBeWellFormed() throws Exception {
        resetTestUser();
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        mockMvc.perform(get("/ussd/error")
                .param(phoneParameter, phoneForTests))
                .andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    /*
    Small helper method to reset testUser between tests
     */

    private void resetTestUser() {

        testUser.setDisplayName(testUserName);
        testUser.setLanguageCode("en");

    }


}
