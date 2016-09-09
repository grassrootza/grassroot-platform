package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.services.GroupPage;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.core.util.DateTimeUtil.convertToSystemTime;
import static za.org.grassroot.core.util.DateTimeUtil.getSAST;
import static za.org.grassroot.webapp.util.USSDUrlUtil.backVoteUrl;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveVoteMenu;


/**
 * Created by luke on 2015/11/27.
 */
public class USSDVoteControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDVoteControllerTest.class);

    private static final String testUserPhone = "27701110000";
    private static final String phoneParam = "msisdn";

    private static final String path = "/ussd/vote/";

    private User testUser;

    @InjectMocks
    private USSDVoteController ussdVoteController;

    @InjectMocks
    private USSDEventUtil ussdEventUtil;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdVoteController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdVoteController);
        ussdEventUtil.setMessageSource(messageSource());
        ussdVoteController.setEventUtil(ussdEventUtil);
        testUser = new User(testUserPhone);

    }

    @Test
    public void voteStartMenuHasGroupsNoUpcomingOrOldVotes() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                                               new Group("tg2", testUser),
                                               new Group("tg3", testUser));

        for (Group testGroup : testGroups) {
            testGroup.addMember(testUser);
        }

        GroupPage pageTestGroups = GroupPage.createFromGroups(testGroups, 0, 3);
        log.info("ZOG: page of test groups looks like: " + pageTestGroups.toString());

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.userHasEventsToView(testUser, EventType.VOTE)).thenReturn(-9);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(true);
        when(permissionBrokerMock.getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3)).
                thenReturn(pageTestGroups);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(2)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
        verify(eventManagementServiceMock, times(1)).userHasEventsToView(testUser, EventType.VOTE);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void voteStartIfNoGroupsShouldDisplay() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.userHasEventsToView(testUser, EventType.VOTE)).thenReturn(-9);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(false);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(1)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3);
        verify(eventManagementServiceMock, times(1)).userHasEventsToView(testUser, EventType.VOTE);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void selectNewVoteWithGroupsShouldWork() throws Exception {

        String urlToSave = USSDSection.VOTES.toPath() + "new";
        GroupPage pageTestGroups = GroupPage.createFromGroups(Arrays.asList(new Group("tg1", testUser), new Group("tg2", testUser)), 0, 3);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.isPartOfActiveGroups(testUser)).thenReturn(true);
        when(permissionBrokerMock.getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3))
                .thenReturn(pageTestGroups);

        mockMvc.perform(get(path + "new").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(permissionBrokerMock, times(4)).getPageOfGroupDTOs(testUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE, 0, 3);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

    @Test
    public void askForIssueShouldWork() throws Exception {

        Group testGroup = new Group("tg", testUser);
        VoteRequest testVote = VoteRequest.makeEmpty();
        testVote.setName("someVote");
        testVote.setParent(testGroup);

        String interruptedUrl = saveVoteMenu("issue", testVote.getUid());
        String revisingUrl = backVoteUrl("issue", testVote.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
        when(eventRequestBrokerMock.createEmptyVoteRequest(testUser.getUid(), testGroup.getUid())).thenReturn(testVote);

        mockMvc.perform(get(path + "issue").param(phoneParam, testUserPhone).param("groupUid", "" + testGroup.getUid())).
                andExpect(status().isOk());
        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + revisingUrl).param(phoneParam, testUserPhone).param("request", "2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(3)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(cacheUtilManagerMock, times(3)).putUssdMenuForUser(testUserPhone, interruptedUrl);
        verify(eventRequestBrokerMock, times(1)).createEmptyVoteRequest(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void askForStandardTimeShouldWork() throws Exception {

        VoteRequest testVote = VoteRequest.makeEmpty();
        String requestUid = testVote.getUid();
        String interruptedUrl = saveVoteMenu("time", requestUid);
        String revisingUrl = backVoteUrl("time", requestUid);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "time").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "test vote")).andExpect(status().isOk());
        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + revisingUrl).param(phoneParam, testUserPhone).param("request", "3")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(3)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).updateName(testUser.getUid(), requestUid, "test vote");
        verifyNoMoreInteractions(eventRequestBrokerMock);

    }

    @Test
    public void askForCustomTimeShouldWork() throws Exception {

        VoteRequest testVote = VoteRequest.makeEmpty();
        String interruptedUrl = saveVoteMenu("time_custom", testVote.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "time_custom").param(phoneParam, testUserPhone).param("entityUid", testVote.getUid())).
                andExpect(status().isOk());
        mockMvc.perform(get("/ussd/" + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void confirmationMenuShouldWork() throws Exception {

        VoteRequest testVote = VoteRequest.makeEmpty();
        String requestUid = testVote.getUid();
        String userUid = testUser.getUid();

        String interruptedUrl = saveVoteMenu("confirm", requestUid);
        LocalDateTime in7minutes = ZonedDateTime.now(getSAST()).plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS).toLocalDateTime();
        LocalDateTime tomorrow5pm = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(17, 0));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(testVote);
        when(learningServiceMock.parse("Tomorrow 5pm"))
                .thenReturn(LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(17, 0)));


        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "1").param("field", "standard").param("time", "instant")).andExpect(status().isOk());
        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "Tomorrow 5pm").param("field", "custom")).andExpect(status().isOk());
        testVote.setEventStartDateTime(convertToSystemTime(tomorrow5pm, getSAST()));
        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "Revised subject").param("field", "issue")).andExpect(status().isOk());
        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(4)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(4)).load(requestUid);
        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(userUid, requestUid, in7minutes);
        verify(eventRequestBrokerMock, times(1)).updateEventDateTime(userUid, requestUid, tomorrow5pm);
        verify(eventRequestBrokerMock, times(1)).updateName(userUid, requestUid, "Revised subject");
        verifyNoMoreInteractions(eventRequestBrokerMock);


    }

    @Test
    public void sendMenuShouldWork() throws Exception {

        Instant testClosingTime = Instant.now().plus(7, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MINUTES);

        VoteRequest testVote = VoteRequest.makeEmpty();
        testVote.setCreatedByUser(testUser);
        testVote.setName("test vote");
        testVote.setEventStartDateTime(testClosingTime);
        String requestUid = testVote.getUid();

        Vote savedVote = new Vote("test vote", testClosingTime, testUser, new Group("tg1", testUser));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(eventRequestBrokerMock.finish(testUser.getUid(), requestUid, true)).thenReturn("fake-UID");
        when(eventBrokerMock.load("fake-UID")).thenReturn(savedVote);

        mockMvc.perform(get(path + "send").param(phoneParam, testUserPhone).param("entityUid", "" + testVote.getUid()))
                .andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).finish(testUser.getUid(), requestUid, true);
        verifyNoMoreInteractions(eventRequestBrokerMock);
        verify(eventBrokerMock, times(1)).load("fake-UID");
        verifyNoMoreInteractions(eventBrokerMock);

    }

}
