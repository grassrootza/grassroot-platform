package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.enums.USSDSection;
import za.org.grassroot.webapp.util.USSDEventUtil;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;
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
        wireUpMessageSourceAndGroupUtil(ussdVoteController, ussdGroupUtil);
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
        Page<Group> pageTestGroups = new PageImpl<Group>(testGroups);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.userHasEventsToView(testUser, EventType.VOTE)).thenReturn(-9);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(pageTestGroups);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(any(User.class), anyInt(), anyInt());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).userHasEventsToView(testUser, EventType.VOTE);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void voteStartIfNoGroupsShouldDisplay() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.userHasEventsToView(testUser, EventType.VOTE)).thenReturn(-9);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(false);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).userHasEventsToView(testUser, EventType.VOTE);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    /*
    Note: not checking interactions with groupManagementService in these because expected number may change once
    we have group permissions implemented.
     */

    @Test
    public void selectNewVoteWithGroupsShouldWork() throws Exception {
        String urlToSave = USSDSection.VOTES.toPath() + "new";
        Page<Group> pageTestGroups = new PageImpl<Group>(Arrays.asList(new Group("tg1", testUser), new Group("tg2", testUser)));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(pageTestGroups);

        mockMvc.perform(get(path + "new").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).hasActiveGroupsPartOf(testUser);
        verify(groupManagementServiceMock, times(2)).getPageOfActiveGroups(testUser, 0, 3);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void askForIssueShouldWork() throws Exception {

        Group testGroup = new Group("tg", testUser);
        testGroup.setId(1L);
        Event testVote = new Vote("someVote", Timestamp.from(Instant.now()), testUser, testGroup, true);
        testVote.setId(1L);
        String interruptedUrl = saveVoteMenu("issue", 1L);
        String revisingUrl = backVoteUrl("issue", testVote.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "issue").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId())).
                andExpect(status().isOk());
        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());
        mockMvc.perform(get(base + revisingUrl).param(phoneParam, testUserPhone).param("request", "2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, interruptedUrl);
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        // verify(eventManagementServiceMock, times(1)).createVote(testUser, testGroup.getId());
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

        Event testVote = new Vote("somevote", Timestamp.from(Instant.now()), testUser, new Group("somegroup", testUser));
        testVote.setId(1L);
        String interruptedUrl = saveVoteMenu("time_custom", 1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "time_custom").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);

        // check interruption and resume works
        mockMvc.perform(get("/ussd/" + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        // check event interaction

    }

    @Test
    public void confirmationMenuShouldWork() throws Exception {

        VoteRequest testVote = VoteRequest.makeEmpty();
        String requestUid = testVote.getUid();
        String userUid = testUser.getUid();

        String interruptedUrl = saveVoteMenu("confirm", requestUid);
        LocalDateTime tomorrow5pm = LocalDateTime.of(LocalDate.now().plusDays(1), LocalTime.of(17, 0));

        when(userManagementServiceMock.findByInputNumber(testUserPhone, interruptedUrl)).thenReturn(testUser);
        when(eventRequestBrokerMock.load(requestUid)).thenReturn(testVote);

        LocalDateTime in7minutes = LocalDateTime.now().plusMinutes(7L).truncatedTo(ChronoUnit.SECONDS);

        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "1").param("field", "standard").param("time", "instant")).andExpect(status().isOk());
        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "Tomorrow 5pm").param("field", "custom")).andExpect(status().isOk());
        testVote.setEventStartDateTime(Timestamp.valueOf(tomorrow5pm));
        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("entityUid", requestUid).
                param("request", "Revised subject").param("field", "issue")).andExpect(status().isOk());
        mockMvc.perform(get(base + interruptedUrl).param(phoneParam, testUserPhone).param("request", "1")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(4)).findByInputNumber(testUserPhone, interruptedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).updateStartTimestamp(userUid, requestUid, Timestamp.valueOf(in7minutes));
        verify(eventRequestBrokerMock, times(1)).updateStartTimestamp(userUid, requestUid, Timestamp.valueOf(tomorrow5pm));
        verify(eventRequestBrokerMock, times(1)).updateName(userUid, requestUid, "Revised subject");
        verify(eventRequestBrokerMock, times(1)).load(requestUid);
        verifyNoMoreInteractions(eventRequestBrokerMock);


    }

    @Test
    public void sendMenuShouldWork() throws Exception {

        Date testClosingTime = DateTimeUtil.addMinutesAndTrimSeconds(new Date(), 7);
        Timestamp testTimestamp = new Timestamp(testClosingTime.getTime());

        VoteRequest testVote = VoteRequest.makeEmpty();
        testVote.setCreatedByUser(testUser);
        testVote.setName("test vote");
        testVote.setEventStartDateTime(testTimestamp);
        String requestUid = testVote.getUid();

        String testParamTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm")).format(testTimestamp);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(eventRequestBrokerMock.finish(testUser.getUid(), requestUid, true)).thenReturn("fake-UID");

        mockMvc.perform(get(path + "send").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId()).
                param("time", testParamTime)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventRequestBrokerMock, times(1)).finish(testUser.getUid(), requestUid, true);
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

}
