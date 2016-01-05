package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

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

    List<User> languageUsers;

    @InjectMocks
    USSDHomeController ussdHomeController;

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
        mockMvc.perform(get(openingMenu + "_language").param(phoneParameter, phoneForTests).param("language", "ts")).
                andExpect(status().isOk());

        testUser.setDisplayName(testUserName);

    }

    @Test
    public void forcedStartMenuShouldWork() throws Exception {

        testUser.setHasInitiatedSession(true);
        testUser.setLastUssdMenu("/ussd/mtg/start"); // irrelevant if this is well formed or not, just testing if it asks

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);

        mockMvc.perform(get(openingMenu).param(phoneParameter, phoneForTests)).andExpect(status().isOk());
        mockMvc.perform(get(openingMenu + "_force").param(phoneParameter, phoneForTests)).andExpect(status().isOk());

        testUser.setLastUssdMenu("");

    }

    @Test
    public void groupJoinTokenShouldWorkInAllLanguages() throws Exception {

        testUser.setDisplayName("");
        testUser.setHasInitiatedSession(false);

        Group testGroup = new Group(testGroupName, new User("27601110000"));

        testGroup.setGroupTokenCode("111");
        testGroup.setTokenExpiryDateTime(new Timestamp(DateTimeUtil.addHoursToDate(new Date(), 24 * 7).getTime()));

        when(userManagementServiceMock.loadOrSaveUser(phoneForTests)).thenReturn(testUser);
        when(groupManagementServiceMock.tokenExists("111")).thenReturn(true);
        when(groupManagementServiceMock.getGroupByToken("111")).thenReturn(testGroup);

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

        Event vote = new Event(testUser, EventType.Vote, true);
        vote.setId(1L); // since we will need this in getting and displaying
        vote.setName("Are unit tests working?");
        vote.setAppliesToGroup(testGroup);

        Map<String, String> voteDetails = new HashMap<>();
        voteDetails.put("groupName", testGroup.getGroupName());
        voteDetails.put("creatingUser", testUser.getDisplayName());
        voteDetails.put("eventSubject", "Are unit tests working?");

        when(eventManagementServiceMock.getEventDescription(1L)).thenReturn(voteDetails);

        List<User> votingUsers = new ArrayList<>(languageUsers);
        votingUsers.add(testUser);

        for (User user : votingUsers) {

            testGroup.addMember(user); // this may be redundant
            user.setHasInitiatedSession(false);
            user.setLastUssdMenu("");

            when(userManagementServiceMock.loadOrSaveUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.needsToVoteOrRSVP(user)).thenReturn(true);
            when(userManagementServiceMock.needsToVote(user)).thenReturn(true);
            when(eventManagementServiceMock.getNextOutstandingVote(user)).thenReturn(vote.getId());

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(userManagementServiceMock, times(1)).needsToVoteOrRSVP(user);
            verify(eventManagementServiceMock, times(1)).getNextOutstandingVote(user);

            // note: the fact that message source accessor is not wired up may mean this is not actually testing
            mockMvc.perform(get("/ussd/vote").param(phoneParameter, user.getPhoneNumber()).param("eventId", "" + vote.getId()).
                    param("response", "yes")).andExpect(status().isOk());

            verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(vote.getId(), user.getPhoneNumber(),
                                                                         EventRSVPResponse.fromString("yes"));
        }
    }

    @Test
    public void meetingRsvpShouldWorkInAllLanguages() throws Exception {

        resetTestUser();
        Group testGroup = new Group(testGroupName, testUser);

        Event meeting = new Event("Meeting about testing", testUser, testGroup, false);
        meeting.setId(2L);

        List<User> groupMembers = new ArrayList<>(languageUsers);
        groupMembers.add(testUser);

        for (User user: groupMembers) {

            user.setHasInitiatedSession(false);
            user.setLastUssdMenu("");

            when(userManagementServiceMock.loadOrSaveUser(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(userManagementServiceMock.needsToVoteOrRSVP(user)).thenReturn(true);
            when(userManagementServiceMock.needsToRSVP(user)).thenReturn(true);
            when(eventManagementServiceMock.getOutstandingRSVPForUser(user)).thenReturn(Arrays.asList(meeting));

            String[] mockMeetingFields = new String[] { testGroup.getGroupName(), testUser.getDisplayName(), meeting.getName(),
                                                        "Tomorrow 10am", "Braam" };

            when(eventManagementServiceMock.populateNotificationFields(meeting)).thenReturn(mockMeetingFields);

            mockMvc.perform(get(openingMenu).param(phoneParameter, user.getPhoneNumber())).andExpect(status().isOk());
            verify(userManagementServiceMock, times(1)).needsToVoteOrRSVP(user);
            verify(eventManagementServiceMock, times(1)).getOutstandingRSVPForUser(user);

            mockMvc.perform(get("/ussd/vote").param(phoneParameter, user.getPhoneNumber()).param("eventId", "" + meeting.getId()).
                    param("response", "yes")).andExpect(status().isOk());

            verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(meeting.getId(), user.getPhoneNumber(), EventRSVPResponse.YES);

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
        when(groupManagementServiceMock.needsToRenameGroup(testUser)).thenReturn(true);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.saveGroup(testGroup)).thenReturn(testGroup);
        when(groupManagementServiceMock.groupToRename(testUser)).thenReturn(testGroup);

        // todo: work out how to verify that it actually returned the prompt to rename the group
        mockMvc.perform(get(openingMenu).param(phoneParameter, testUser.getPhoneNumber())).
                andExpect(status().isOk());

        mockMvc.perform(get("/ussd/group-start").param(phoneParameter, phoneForTests).param("groupId", "" + testGroup.getId()).
                param("request", testGroupName)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrSaveUser(phoneForTests);
        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(groupManagementServiceMock, times(1)).saveGroup(testGroup);
        verify(groupManagementServiceMock, times(2)).needsToRenameGroup(testUser);
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
    Make sure group pagination works
     */
    @Test
    public void groupSecondPageShouldWork() throws Exception {

        resetTestUser();

        Group testGroup1 = new Group();
        List<Group> testGroups = Arrays.asList(new Group("gc1", testUser),
                                               new Group("gc2", testUser),
                                               new Group("gc3", testUser),
                                               new Group("gc4", testUser));

        Page<Group> groupPage = new PageImpl<Group>(testGroups, new PageRequest(1, 3), 4); // need to work out how to do

        when(userManagementServiceMock.findByInputNumber(phoneForTests)).thenReturn(testUser);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 1, 3)).thenReturn(groupPage);

        mockMvc.perform(get("/ussd/group_page").param(phoneParameter, phoneForTests).param("prompt", "Look at pages").
                param("page", "1").param("existingUri", "/ussd/blank").param("newUri", "/ussd/blank2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(phoneForTests);
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(testUser, 1, 3);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
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
        testUser.setLastUssdMenu("");

    }


}
