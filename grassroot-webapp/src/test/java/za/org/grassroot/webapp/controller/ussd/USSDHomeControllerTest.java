package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.Vote;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.services.GroupPage;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/11/20.
 */
public class USSDHomeControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

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

    @InjectMocks
    private USSDHomeController ussdHomeController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdHomeController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdHomeController, ussdGroupUtil);

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
        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);

        // todo: check a lot more than this
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrSaveUser(phoneForTests);
        // verifyNoMoreInteractions(userManagementServiceMock); // todo: work out if should shift other calls to user entity itself
    }

    @Test
    public void welcomeMenuAfterChoosingLanguageShouldWork() throws Exception {

        testUser.setHasInitiatedSession(true);
        testUser.setLanguageCode("zu");

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
    }

    @Test
    public void welcomeMenuLocaleShouldWorkWithNoName() throws Exception {

        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);
        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
        testUser.setLanguageCode("ts");
        when(userManagementServiceMock.setUserLanguage(testUser, "ts")).thenReturn(testUser);
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).
                andExpect(status().isOk());

        testUser.setDisplayName(testUserName);

    }

    @Test
    public void forcedStartMenuShouldWork() throws Exception {

        testUser.setHasInitiatedSession(true);
        // switch the next thing to cache manager
        // testUser.setLastUssdMenu("/ussd/mtg/start"); // irrelevant if this is well formed or not, just testing if it asks

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
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
        testGroup.setTokenExpiryDateTime(Timestamp.valueOf(LocalDateTime.now().plus(1, ChronoUnit.WEEKS)));

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(groupBrokerMock.findGroupFromJoinCode("111")).thenReturn(testGroup);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests).param("request", "*134*1994*111#")).
                andExpect(status().isOk());

        for (User user : languageUsers) {
            when(userManagementServiceMock.loadOrSaveUser(user.getPhoneNumber())).thenReturn(user);
            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber()).param("request", "*134*1994*111#")).
                    andExpect(status().isOk());
        }

    }



    @Test
    public void voteRequestScreenShouldWorkInAllLanguages() throws Exception {

        testUser.setDisplayName(testUserName);
        testUser.setLanguageCode("en");
        Group testGroup = new Group(testGroupName, testUser);

//        Event vote = new Event(testUser, EventType.VOTE, true);
        Vote vote = new Vote("are unit tests working?", Instant.now().plus(1, ChronoUnit.HOURS), testUser, testGroup);
        vote.setId(1L);

        List<User> votingUsers = new ArrayList<>(languageUsers);
        votingUsers.add(testUser);

        for (User user : votingUsers) {

            testGroup.addMember(user); // this may be redundant
            user.setHasInitiatedSession(false);

            when(userManagementServiceMock.loadOrSaveUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.needsToVote(user)).thenReturn(true);
            when(eventManagementServiceMock.getOutstandingVotesForUser(user)).thenReturn(Collections.singletonList(vote));
            when(eventBrokerMock.load(vote.getUid())).thenReturn(vote);

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(userManagementServiceMock, times(1)).needsToVote(user);
            verify(eventManagementServiceMock, times(1)).getOutstandingVotesForUser(user);

            // note: the fact that message source accessor is not wired up may mean this is not actually testing
            mockMvc.perform(get("/ussd/start").param(phoneParameter, user.getPhoneNumber()));
            mockMvc.perform(get("/ussd/vote").param(phoneParameter, user.getPhoneNumber()).
                    param("entityUid", "" + vote.getUid()).
                    param("response", "yes")).andExpect(status().isOk());

            verify(eventLogBrokerMock, times(1)).rsvpForEvent(vote.getUid(), user.getUid(),
                                                                         EventRSVPResponse.YES);
        }
    }

    @Test
    public void meetingRsvpShouldWorkInAllLanguages() throws Exception {

        resetTestUser();
        Group testGroup = new Group(testGroupName, testUser);

        Meeting meeting = new Meeting("Meeting about testing", Instant.now(), testUser, testGroup, "someLocation");
        meeting.setId(2L);

        List<User> groupMembers = new ArrayList<>(languageUsers);
        groupMembers.add(testUser);

        for (User user: groupMembers) {

            user.setHasInitiatedSession(false);

            when(userManagementServiceMock.loadOrSaveUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.needsToRSVP(user)).thenReturn(true);
            when(eventManagementServiceMock.getOutstandingRSVPForUser(user)).thenReturn(Collections.singletonList(meeting));
            when(eventBrokerMock.loadMeeting(meeting.getUid())).thenReturn(meeting);

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(eventManagementServiceMock, times(1)).getOutstandingRSVPForUser(user);

            mockMvc.perform(get("/ussd/rsvp").param(phoneParameter, user.getPhoneNumber())
                                    .param("entityUid", "" + meeting.getUid())
                                    .param("confirmed", "yes")).andExpect(status().isOk());

            verify(eventLogBrokerMock, times(1)).rsvpForEvent(meeting.getUid(), user.getUid(), EventRSVPResponse.YES);

        }

    }

    /*
    Make sure groupRename works properly
     */
    @Test
    public void groupRenameShouldWork() throws Exception {

        resetTestUser();
        testUser.setHasInitiatedSession(true);
        Group testGroup = new Group("", testUser);
        testGroup.setId(0L);
        testGroup.setCreatedDateTime(Timestamp.valueOf(LocalDateTime.now()));

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.fetchGroupUserMustRename(testUser)).thenReturn(testGroup);

        // todo: work out how to verify that it actually returned the prompt to rename the testGroup
        mockMvc.perform(get(openingMenu).param(phoneParameter, testUser.getPhoneNumber())).
                andExpect(status().isOk());

        mockMvc.perform(get("/ussd/group-start").
                param(phoneParameter, phoneForTests).
                param("groupUid", "" + testGroup.getUid()).
                param("request", testGroupName)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrSaveUser(phoneForTests);
        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(groupBrokerMock, times(1)).updateName(testUser.getUid(), testGroup.getUid(), testGroupName);
        verify(userManagementServiceMock, times(2)).fetchGroupUserMustRename(testUser);
    }

    /*
    User rename should work properly
     */
    @Test
    public void userRenamePromptShouldWork() throws Exception {

        resetTestUser();
        testUser.setHasInitiatedSession(true);
        testUser.setDisplayName("");

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.needsToRenameSelf(testUser)).thenReturn(true);

        // todo: as above, work how to verify what it returned (once / if this is re-enabled
        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).
                andExpect(status().isOk());

        testUser.setDisplayName(testUserName); // necessary else when/then doesn't work within controller
        when(userManagementServiceMock.setDisplayName(testUser, testUserName)).thenReturn(testUser);

        mockMvc.perform(get("/ussd/rename-start").param(phoneParameter, phoneForTests).param("request", testUserName)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrSaveUser(phoneForTests);
        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(userManagementServiceMock, times(1)).setDisplayName(testUser, testUserName);

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

        GroupPage groupPage = GroupPage.createFromGroups(testGroups, 1, 3);

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(permissionBrokerMock.getPageOfGroupDTOs(testUser, null, 1, 3)).thenReturn(groupPage);

        mockMvc.perform(get("/ussd/group_page").param(phoneParameter, phoneForTests).param("prompt", "Look at pages").
                param("page", "1").param("existingUri", "/ussd/blank").param("newUri", "/ussd/blank2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(permissionBrokerMock, times(1)).getPageOfGroupDTOs(testUser, null, 1, 3);
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
        mockMvc.perform(get("/ussd/error").param(phoneParameter, phoneForTests)).
                andExpect(status().isOk());
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
