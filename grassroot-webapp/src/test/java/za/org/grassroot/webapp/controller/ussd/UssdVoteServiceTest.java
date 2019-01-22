package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import za.org.grassroot.core.domain.RoleName;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.core.domain.task.VoteRequest;
import za.org.grassroot.core.enums.UserInterfaceType;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static za.org.grassroot.core.domain.Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE;


/**
 * Created by luke on 2015/11/27.
 */
public class UssdVoteServiceTest extends UssdUnitTest {
    private static final String testUserPhone = "27701110000";


    private User testUser;
    private UssdVoteService ussdVoteService;
    private UssdHomeService ussdHomeService;

    @Before
    public void setUp() {
        testUser = new User(testUserPhone, null, null);
        this.ussdVoteService = new UssdVoteServiceImpl(eventBrokerMock, ussdSupport, voteBrokerMock, userManagementServiceMock, cacheUtilManagerMock, permissionBrokerMock, ussdEventUtil, ussdGroupUtil, eventRequestBrokerMock, userLoggerMock, accountFeaturesBrokerMock);
        this.ussdHomeService = new UssdHomeServiceImpl(null, locationInfoBrokerMock, null, userManagementServiceMock, campaignBrokerMock, null, userLoggerMock, ussdSupport, cacheUtilManagerMock, null, ussdVoteService, null, userResponseBrokerMock, groupQueryBrokerMock, accountFeaturesBrokerMock, groupBrokerMock, null);
    }

    @Test
    public void voteRequestScreenShouldWorkInAllLanguages() throws Exception {
        testUser = new User(testUserPhone, "test user", null);
        Group testGroup = new Group("test group", testUser);
        testGroup.addMember(testUser, RoleName.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        Vote vote = new Vote("are unit tests working?", Instant.now().plus(1, ChronoUnit.HOURS), testUser, testGroup);

        List<User> votingUsers = new ArrayList<>(languageUsers);
        votingUsers.add(testUser);

        for (User user : votingUsers) {

            testGroup.addMember(user, RoleName.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null); // this may be redundant
            user.setHasInitiatedSession(false);

            when(userManagementServiceMock.loadOrCreateUser(user.getPhoneNumber(), UserInterfaceType.USSD)).thenReturn(user);
            when(userManagementServiceMock.findByInputNumber(user.getPhoneNumber())).thenReturn(user);
            when(userResponseBrokerMock.checkForEntityForUserResponse(user.getUid(), true)).thenReturn(vote);

            this.ussdHomeService.processStartMenu(user.getPhoneNumber(), null);
            verify(userResponseBrokerMock, times(1)).checkForEntityForUserResponse(user.getUid(), true);

            // note: the fact that message source accessor is not wired up may mean this is not actually testing
            when(eventBrokerMock.load(vote.getUid())).thenReturn(vote);
            this.ussdHomeService.processStartMenu(user.getPhoneNumber(), null);

            this.ussdVoteService.processVoteAndWelcome(user.getPhoneNumber(), vote.getUid(), "yes");
            verify(voteBrokerMock, times(1)).recordUserVote(user.getUid(), vote.getUid(), "yes");
        }
    }

    @Test
    public void yesNoVoteMenuShouldReturnGroupList() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                                               new Group("tg2", testUser),
                                               new Group("tg3", testUser));

        testGroups.forEach(tg -> tg.addMember(testUser, RoleName.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null));

        when(userManagementServiceMock.findByInputNumber(eq(testUserPhone), anyString())).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(3);
        when(permissionBrokerMock.getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3)).thenReturn(testGroups);

        this.ussdVoteService.processYesNoSelectGroup(testUserPhone, VoteRequest.makeEmpty().getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(eq(testUserPhone), anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(2)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

//    @Test
//    public void voteStartIfNoGroupsShouldDisplay() throws Exception {
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(0);
//
//        mockMvc.perform(get(path + "start")
//                .param(phoneParam, testUserPhone))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(permissionBrokerMock, times(2)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE);
//        verifyNoMoreInteractions(permissionBrokerMock);
//    }

    @Test
    public void selectVoteForMultiOptionVoteShouldWork() throws Exception {
        VoteRequest voteRequest = VoteRequest.makeEmpty();
        voteRequest.setVoteOptions(Arrays.asList("Option 1", "Option 2"));
        String urlToSave = "vote/multi_option/add?requestUid=" + voteRequest.getUid() + "&interrupted=1&priorInput=0";
        List<Group> validGroups = Arrays.asList(new Group("tg1", testUser), new Group("tg2", testUser));

        // todo : add test for user with just one group
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(voteRequest.getUid())).thenReturn(voteRequest);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(2);
        when(permissionBrokerMock.getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3)).thenReturn(validGroups);

        this.ussdVoteService.processAddVoteOption(testUserPhone, voteRequest.getUid(), "0", null);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(2)).countActiveGroupsWithPermission(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

//    @Test
//    public void askForIssueShouldWork() throws Exception {
//        VoteRequest testVote = VoteRequest.makeEmpty();
//        testVote.setName("someVote");
//
//        String interruptedUrl = saveVoteMenu("subject", testVote.getUid());
//        String revisingUrl = backVoteUrl("subject", testVote.getUid());
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "subject")
//                .param(phoneParam, testUserPhone)).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + revisingUrl).param(phoneParam, testUserPhone).param("request", "2")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(3)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(cacheUtilManagerMock, times(2)).putUssdMenuForUser(testUserPhone, interruptedUrl);
//        verifyNoMoreInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void askForStandardTimeShouldWork() throws Exception {
//        Group testGroup = new Group("tg", testUser);
//        VoteRequest testVote = VoteRequest.makeEmpty();
//        String requestUid = testVote.getUid();
//        String interruptedUrl = saveVoteMenu("closing", requestUid) + "&groupUid=" + testGroup.getUid();
//        String revisingUrl = backVoteUrl("closing", requestUid);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveVoteMenu("closing", requestUid))).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "closing")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", requestUid)
//                .param("groupUid", testGroup.getUid()))
//                .andExpect(status().isOk());
//        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + revisingUrl).param(phoneParam, testUserPhone).param("request", "3")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, interruptedUrl);
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveVoteMenu("closing", requestUid));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(2)).updateVoteGroup(testUser.getUid(), requestUid, testGroup.getUid());
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void askForCustomTimeShouldWork() throws Exception {
//        VoteRequest testVote = VoteRequest.makeEmpty();
//        String interruptedUrl = saveVoteMenu("time_custom", testVote.getUid());
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "time_custom")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", testVote.getUid())).
//                andExpect(status().isOk());
//        mockMvc.perform(get("/ussd/" + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, interruptedUrl);
//        verifyNoMoreInteractions(userManagementServiceMock);
//    }
//
//    @Test
//    public void confirmationMenuShouldWork() throws Exception {
//
//        VoteRequest testVote = VoteRequest.makeEmpty();
//        String requestUid = testVote.getUid();
//        String userUid = testUser.getUid();
//
//        String interruptedUrl = saveVoteMenu("confirm", requestUid);
//        LocalDateTime in7minutes = ZonedDateTime.now(getSAST()).plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS).toLocalDateTime();
//        LocalDateTime tomorrow5pm = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(17, 0));
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
//        when(eventRequestBrokerMock.load(requestUid)).thenReturn(testVote);
//        when(learningServiceMock.parse("Tomorrow 5pm"))
//                .thenReturn(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(17, 0)));
//
//        testVote.setEventStartDateTime(in7minutes.toInstant(ZoneOffset.UTC));
//        mockMvc.perform(get(path + "confirm")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", requestUid)
//                .param("request", "1")
//                .param("field", "standard")
//                .param("time", "INSTANT"))
//                .andExpect(status().isOk());
//
//        mockMvc.perform(get(path + "confirm")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", requestUid)
//                .param("request", "Tomorrow 5pm")
//                .param("field", "custom"))
//                .andExpect(status().isOk());
//
//        testVote.setEventStartDateTime(convertToSystemTime(tomorrow5pm, getSAST()));
//        mockMvc.perform(get(path + "confirm")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", requestUid)
//                .param("request", "Revised subject")
//                .param("field", "subject"))
//                .andExpect(status().isOk());
//
//        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(4)).findByInputNumber(testUserPhone, interruptedUrl);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(5)).load(requestUid);
//        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(userUid, requestUid, in7minutes);
//        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(userUid, requestUid, tomorrow5pm);
//        verify(eventRequestBrokerMock, times(1)).updateName(userUid, requestUid, "Revised subject");
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//    }
//
//    @Test
//    public void sendMenuShouldWork() throws Exception {
//
//        Instant testClosingTime = Instant.now().plus(7, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);
//
//        VoteRequest testVote = VoteRequest.makeEmpty();
//        testVote.setCreatedByUser(testUser);
//        testVote.setName("test vote");
//        testVote.setEventStartDateTime(testClosingTime);
//        String requestUid = testVote.getUid();
//
//        Vote savedVote = new Vote("test vote", testClosingTime, testUser, new Group("tg1", testUser));
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        when(eventRequestBrokerMock.finish(testUser.getUid(), requestUid, true)).thenReturn("fake-UID");
//        when(eventBrokerMock.load("fake-UID")).thenReturn(savedVote);
//        when(accountFeaturesBrokerMock.numberEventsLeftForParent("fake-UID")).thenReturn(99);
//
//        mockMvc.perform(get(path + "send")
//                .param(phoneParam, testUserPhone)
//                .param("requestUid", testVote.getUid()))
//                .andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(eventRequestBrokerMock, times(1)).finish(testUser.getUid(), requestUid, true);
//        verifyNoMoreInteractions(eventRequestBrokerMock);
//        verify(eventBrokerMock, times(1)).load("fake-UID");
//        verifyNoMoreInteractions(eventBrokerMock);
//
//    }

}