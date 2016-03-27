package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.RSVPTotalsDTO;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.webapp.controller.BaseController;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Created by paballo on 2016/01/22.
 */
public class VoteControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);
    private static final Long dummyId = 1L;

    @InjectMocks
    private VoteController voteController;

    @Before
    public void setUp() {
        setUp(voteController);


    }

    @Test
    public void createVoteWorksWhenGroupIdSpecified() throws Exception {
        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        testGroup.setId(dummyId);
        List<Group> testPossibleGroups = new ArrayList<>();
        testPossibleGroups.add(testGroup);
        Event testVote = null;
        testVote.setId(dummyId);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/vote/create").param("groupId", String.valueOf(dummyId))).andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("group",
                        hasProperty("id", is(1L))));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, testGroup, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void createVoteWorksWhengroupNotSpecified() throws Exception {
        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        testGroup.setId(dummyId);
        Set<Group> testPossibleGroups = new HashSet<>();
        testPossibleGroups.add(testGroup);
        Event testVote = null;
        testVote.setId(dummyId);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(testPossibleGroups);
        mockMvc.perform(get("/vote/create")).andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("possibleGroups",
                        hasItem(testGroup)));
        verify(permissionBrokerMock, times(1)).getActiveGroups(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void voteCreateDoWorks() throws Exception {

//        Event testVote = new Event(sessionTestUser, EventType.VOTE, true);
        Event testVote = null; // todo: new design?
        testVote.setId(dummyId);
        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        testGroup.setId(dummyId);
        // when(groupManagementServiceMock.canUserCallVote(dummyId, sessionTestUser)).thenReturn(true);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(post("/vote/create").param("selectedGroupId", String.valueOf(dummyId))
                .sessionAttr("vote", testVote))
                .andExpect(model().attribute("eventId", is(dummyId)))
                .andExpect(model().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("vote/view"));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        // todo: eventBroker verify
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void viewVoteWorks() throws Exception {

        Event testVote = new Vote("test", Timestamp.from(Instant.now()), sessionTestUser, new Group("tg1", sessionTestUser));
        when(eventBrokerMock.load(testVote.getUid())).thenReturn(testVote);
        RSVPTotalsDTO testVoteResults = new RSVPTotalsDTO();
        testVoteResults.setYes(3);
        testVoteResults.setNo(7);
        testVoteResults.setMaybe(3);
        testVoteResults.setNumberOfUsers(15);
        when(eventManagementServiceMock.getVoteResultsDTO(testVote)).thenReturn(testVoteResults);
        mockMvc.perform(get("/vote/view").param("eventId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("vote/view"))
                .andExpect(model().attribute("yes", is(testVoteResults.getYes())))
                .andExpect(model().attribute("no", is(testVoteResults.getNo())))
                .andExpect(model().attribute("abstained", is(testVoteResults.getMaybe())))
                .andExpect(model().attribute("possible", is(testVoteResults.getNumberOfUsers())))
                .andExpect(model().attribute("vote", hasProperty("id", is(dummyId))));
        verify(eventBrokerMock, times(1)).load(testVote.getUid());
        verify(eventManagementServiceMock, times(1)).getVoteResultsDTO(testVote);
        verifyNoMoreInteractions(eventManagementServiceMock);


    }

    @Test
    public void answerVoteWorks() throws Exception {

//        Event testVote = new Event(sessionTestUser, EventType.VOTE, true);
        Event testVote = null; // todo: new design?
        testVote.setId(dummyId);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(testVote);
        // todo: new design?
/*
        when(eventLogManagementServiceMock.rsvpForEvent(testVote, sessionTestUser, EventRSVPResponse.fromString("yes")))
                .thenReturn(new EventLog());
*/
        mockMvc.perform(post("/vote/answer").param("eventId", String.valueOf(dummyId)).param("answer", "yes"))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/home"))
                .andExpect(view().name("redirect:/home")).andExpect(flash()
                .attributeExists(BaseController.MessageType.INFO.getMessageKey()));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventLogManagementServiceMock, times(1)).rsvpForEvent(testVote, sessionTestUser, EventRSVPResponse.fromString("yes"));
        verifyNoMoreInteractions(eventManagementServiceMock);
        verifyNoMoreInteractions(eventLogManagementServiceMock);

    }


}
