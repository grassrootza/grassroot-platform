package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.task.VoteBroker;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/11/20.
 */
public class USSDHomeControllerTest extends USSDAbstractUnitTest {

    // private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

    private static final String phoneForTests = "27810001111";
    private static final String baseForOthers = "2781000111";
    private static final String testUserName = "Test User";
    private static final String testGroupName = "Test Group";

    private static final String openingMenu = "/ussd/start";
    private static final String phoneParameter = "msisdn";

    private User testUser = new User(phoneForTests, testUserName);

    private User testUserZu = new User(baseForOthers + "2");
    private User testUserTs = new User(baseForOthers + "3");
    private User testUserNso = new User(baseForOthers + "4");
    private User testUserSt = new User(baseForOthers + "5");

    private List<User> languageUsers;

    @Mock
    private VoteBroker voteBrokerMock;

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

        wireUpMessageSourceAndGroupUtil(ussdHomeController);
        wireUpMessageSourceAndGroupUtil(voteController);
        // todo : extend this parrent into method above, to remove public setters
        ReflectionTestUtils.setField(ussdHomeController, "safetyCode", "911");
        ReflectionTestUtils.setField(ussdHomeController, "livewireSuffix", "411");
        ReflectionTestUtils.setField(ussdHomeController, "sendMeLink", "123");
        ReflectionTestUtils.setField(ussdHomeController, "hashPosition", 9);
        ReflectionTestUtils.setField(ussdHomeController, "promotionSuffix", "44");

        /* We use these quite often */
        testUserZu.setLanguageCode("zu");
        testUserTs.setLanguageCode("ts");
        testUserNso.setLanguageCode("nso");
        testUserSt.setLanguageCode("st");

        languageUsers = Arrays.asList(testUserNso, testUserSt, testUserTs, testUserZu);
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

        // todo: check a lot more than this
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(phoneForTests);
        // verifyNoMoreInteractions(userManagementServiceMock); // todo: work out if should shift other calls to user entity itself
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

        Group testGroup = new Group(testGroupName, new User("27601110000"));

        testGroup.setGroupTokenCode("111");
        testGroup.setTokenExpiryDateTime(Instant.now().plus(7, ChronoUnit.DAYS));

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(groupQueryBrokerMock.findGroupFromJoinCode("111")).thenReturn(Optional.of(testGroup));

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests).param("request", "*134*1994*111#")).
                andExpect(status().isOk());

        for (User user : languageUsers) {
            when(userManagementServiceMock.loadOrCreateUser(user.getPhoneNumber())).thenReturn(user);
            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber()).param("request", "*134*1994*111#")).
                    andExpect(status().isOk());
        }
    }

    @Test
    public void voteRequestScreenShouldWorkInAllLanguages() throws Exception {

        testUser.setDisplayName(testUserName);
        testUser.setLanguageCode("en");
        Group testGroup = new Group(testGroupName, testUser);
        Vote vote = new Vote("are unit tests working?", Instant.now().plus(1, ChronoUnit.HOURS), testUser, testGroup);

        List<User> votingUsers = new ArrayList<>(languageUsers);
        votingUsers.add(testUser);

        for (User user : votingUsers) {

            testGroup.addMember(user); // this may be redundant
            user.setHasInitiatedSession(false);

            when(userManagementServiceMock.loadOrCreateUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(eventBrokerMock.userHasResponsesOutstanding(user, EventType.VOTE)).thenReturn(true);
            when(eventBrokerMock.getOutstandingResponseForUser(user, EventType.VOTE)).thenReturn(Collections.singletonList(vote));
            when(eventBrokerMock.load(vote.getUid())).thenReturn(vote);

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(eventBrokerMock, times(1)).userHasResponsesOutstanding(user, EventType.VOTE);
            verify(eventBrokerMock, times(1)).getOutstandingResponseForUser(user, EventType.VOTE);

            // note: the fact that message source accessor is not wired up may mean this is not actually testing
            mockMvc.perform(get("/ussd/start").param(phoneParameter, user.getPhoneNumber()));
            mockMvc.perform(get("/ussd/vote/record")
                    .param(phoneParameter, user.getPhoneNumber())
                    .param("voteUid", "" + vote.getUid())
                    .param("response", "yes")).andExpect(status().isOk());

            verify(voteBrokerMock, times(1)).recordUserVote(user.getUid(), vote.getUid(), "yes");
        }
    }

    @Test
    public void meetingRsvpShouldWorkInAllLanguages() throws Exception {
        resetTestUser();
        Group testGroup = new Group(testGroupName, testUser);
        Meeting meeting = new MeetingBuilder().setName("Meeting about testing").setStartDateTime(Instant.now()).setUser(testUser).setParent(testGroup).setEventLocation("someLocation").createMeeting();

        List<User> groupMembers = new ArrayList<>(languageUsers);
        groupMembers.add(testUser);

        for (User user: groupMembers) {

            user.setHasInitiatedSession(false);

            when(userManagementServiceMock.loadOrCreateUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(eventBrokerMock.userHasResponsesOutstanding(user, EventType.MEETING)).thenReturn(true);
            when(eventBrokerMock.getOutstandingResponseForUser(user, EventType.MEETING)).thenReturn(Collections.singletonList(meeting));
            when(eventBrokerMock.loadMeeting(meeting.getUid())).thenReturn(meeting);

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(eventBrokerMock, times(1)).getOutstandingResponseForUser(user, EventType.MEETING);

            mockMvc.perform(get("/ussd/rsvp")
                    .param(phoneParameter, user.getPhoneNumber())
                    .param("entityUid", "" + meeting.getUid())
                    .param("confirmed", "yes")).andExpect(status().isOk());

            verify(eventLogBrokerMock, times(1)).rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);
        }
    }

    @Test
    public void shouldAssembleLiveWire() throws Exception {
             Group group = new Group(testGroupName, testUser);
             Meeting meeting = new MeetingBuilder().setName("").setStartDateTime(Instant.now().plus(1, ChronoUnit.HOURS)).setUser(testUser).setParent(group).setEventLocation("").createMeeting();

             List<Meeting> newMeeting  = Arrays.asList(meeting);

             when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
             when(liveWireBrokerMock.countGroupsForInstantAlert(testUser.getUid())).
                thenReturn(0L);

              mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)
                     .param("request", "*134*1994*411#")).
                andExpect(status().isOk());

              verify(userManagementServiceMock,times(1)).
                loadOrCreateUser(phoneForTests);
              verify(liveWireBrokerMock,times(1)).
                      meetingsForAlert(testUser.getUid());
              verify(liveWireBrokerMock, times(1))
                .countGroupsForInstantAlert(testUser.getUid());

              when(liveWireBrokerMock.countGroupsForInstantAlert(testUser.getUid()))
                      .thenReturn(2L);

               mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)
                .param("request", "*134*1994*411#")).
                       andExpect(status().isOk());

              when(liveWireBrokerMock.meetingsForAlert(testUser.getUid())).
                      thenReturn(newMeeting);
              mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)
                .param("request", "*134*1994*411#").
                              param("page","1")).
                andExpect(status().isOk());
    }

    /*
    User rename should work properly
     */
    @Test
    public void userRenamePromptShouldWork() throws Exception {

        resetTestUser();
        testUser.setHasInitiatedSession(true);
        testUser.setDisplayName("");

        when(userManagementServiceMock.loadOrCreateUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.needsToRenameSelf(testUser)).thenReturn(true);

        // todo: as above, work how to verify what it returned (once / if this is re-enabled
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).
                andExpect(status().isOk());

        testUser.setDisplayName(testUserName); // necessary else when/then doesn't work within controller

        mockMvc.perform(get("/ussd/rename-start").param(phoneParameter, phoneForTests).param("request", testUserName)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(phoneForTests);
        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(userManagementServiceMock, times(1)).updateDisplayName(testUser.getUid(), testUserName);

    }

    /*
    Make sure testGroup pagination works
     */
    @Test
    public void groupSecondPageShouldWork() throws Exception {

        resetTestUser();

        List<Group> testGroups = Arrays.asList(new Group("gc1", testUser),
                                               new Group("gc2", testUser),
                                               new Group("gc3", testUser),
                                               new Group("gc4", testUser));

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, null)).thenReturn(4);
        when(permissionBrokerMock.getPageOfGroups(testUser, null, 1, 3)).thenReturn(testGroups);

        mockMvc.perform(get("/ussd/group_page").param(phoneParameter, phoneForTests).param("prompt", "Look at pages").
                param("page", "1").param("existingUri", "/ussd/blank").param("newUri", "/ussd/blank2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, null);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, null, 1, 3);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(permissionBrokerMock);
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
