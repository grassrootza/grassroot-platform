package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.webapp.util.USSDEventUtil;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
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
    USSDVoteController ussdVoteController;

    @InjectMocks
    USSDEventUtil ussdEventUtil;

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
    public void voteStartMenuHasGroupsNoUpcomingVotes() throws Exception {

        List<Group> testGroups = Arrays.asList(new Group("tg1", testUser),
                                               new Group("tg2", testUser),
                                               new Group("tg3", testUser));
        testUser.setGroupsPartOf(testGroups);
        Page<Group> pageTestGroups = new PageImpl<Group>(testGroups);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.canUserCallVoteOnAnyGroup(testUser)).thenReturn(true);
        when(groupManagementServiceMock.getPageOfActiveGroups(testUser, 0, 3)).thenReturn(pageTestGroups);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        // verify(groupManagementServiceMock, times(1)).canUserCallVoteOnAnyGroup(testUser);
        // verify(groupManagementServiceMock, times(1)).getPageOfActiveGroups(any(User.class), anyInt(), anyInt());
        // verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void voteStartIfNoGroupsShouldDisplay() throws Exception {

        testUser.setGroupsPartOf(new ArrayList<>());

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.canUserCallVoteOnAnyGroup(testUser)).thenReturn(false);

        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(anyString());
        verifyNoMoreInteractions(userManagementServiceMock);
        // verify(groupManagementServiceMock, times(1)).canUserCallVoteOnAnyGroup(any(User.class));
        // verifyNoMoreInteractions(groupManagementServiceMock);

    }

    /*
    Note: not checking interactions with groupManagementService in these because expected number may change once
    we have group permissions implemented.
     */

    @Test
    public void askForIssueShouldWork() throws Exception {

        Group testGroup = new Group("tg", testUser);
        testGroup.setId(1L);
        Event testVote = new Event(testUser, EventType.Vote, true);
        testVote.setId(1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(eventManagementServiceMock.createVote(testUser, testGroup.getId())).thenReturn(testVote);

        mockMvc.perform(get(path + "issue").param(phoneParam, testUserPhone).param("groupId", "" + testGroup.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, saveVoteMenu("issue", 1L));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).createVote(testUser, testGroup.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void askForStandardTimeShouldWork() throws Exception {

        Event testVote = new Event(testUser, EventType.Vote, true);
        testVote.setId(1L);
        String savedUrl = saveVoteMenu("time", 1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, savedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "time").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId()).
                param("request", "test vote")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, savedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setSubject(testVote.getId(), "test vote");
        verifyNoMoreInteractions(eventManagementServiceMock);

        log.info("URL we will be passing back ... " + get(savedUrl).param(phoneParam, testUserPhone).param("request", "1").toString());

        // finally: check the interruption and resume still works
        mockMvc.perform(get("/ussd/" + savedUrl).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, savedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).setSubject(testVote.getId(), "test vote");
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

    @Test
    public void askForCustomTimeShouldWork() throws Exception {

        Event testVote = new Event(testUser, EventType.Vote, true);
        testVote.setId(1L);
        String savedUrl = saveVoteMenu("time_custom", 1L);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, savedUrl)).thenReturn(testUser);

        mockMvc.perform(get(path + "time_custom").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, savedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);

        // check interruption and resume works
        mockMvc.perform(get("/ussd/" + savedUrl).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, savedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        // check event interaction

    }

    @Test
    public void confirmationMenuShouldWork() throws Exception {

        Event testVote = new Event(testUser, EventType.Vote, true);
        testVote.setName("test vote");
        testVote.setEventStartDateTime(Timestamp.valueOf(LocalDateTime.now().plusDays(7)));
        testVote.setId(1L);
        String savedUrl = saveVoteMenu("confirm", 1L);

        // todo: fix these up
        when(userManagementServiceMock.findByInputNumber(testUserPhone, savedUrl)).thenReturn(testUser);
        when(eventManagementServiceMock.loadEvent(testVote.getId())).thenReturn(testVote);
        when(eventManagementServiceMock.setEventTimestamp(anyLong(), any(Timestamp.class))).thenReturn(testVote);

        // todo: the range of variations for this, in particular different times
        mockMvc.perform(get(path + "confirm").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId()).
                param("request", "1").param("field", "standard").param("time", "instant")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, savedUrl);
        verifyNoMoreInteractions(userManagementServiceMock);
        //verify(eventManagementServiceMock, times(1)).loadEvent(testVote.getId());
        //verifyNoMoreInteractions(eventManagementServiceMock);

        // check that interruption works (and do so for all the variations as well
        mockMvc.perform(get("/ussd/" + savedUrl).param(phoneParam, testUserPhone).param("request", "1")).andExpect(status().isOk());
        // verifications

    }

    @Test
    public void sendMenuShouldWork() throws Exception {

        Event testVote = new Event(testUser, EventType.Vote, true);
        testVote.setName("test vote");
        testVote.setId(1L);

        Date testClosingTime = DateTimeUtil.addMinutesAndTrimSeconds(new Date(), 7);

        Timestamp testTimestamp = new Timestamp(testClosingTime.getTime());
        testVote.setEventStartDateTime(testTimestamp);
        String testParamTime = (new SimpleDateFormat("yyyy-MM-dd HH:mm")).format(testTimestamp);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(eventManagementServiceMock.removeSendBlock(testVote.getId())).thenReturn(testVote);

        mockMvc.perform(get(path + "send").param(phoneParam, testUserPhone).param("eventId", "" + testVote.getId()).
                param("time", testParamTime)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).removeSendBlock(testVote.getId());
        verifyNoMoreInteractions(eventManagementServiceMock);

    }

}
