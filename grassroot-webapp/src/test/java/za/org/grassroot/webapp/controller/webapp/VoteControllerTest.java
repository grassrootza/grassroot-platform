package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.EventLog;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.enums.EventRSVPResponse;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Event testVote = new Event();
        testVote.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(groupManagementServiceMock.canUserCallVote(dummyId, sessionTestUser)).thenReturn(true);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(testPossibleGroups);
        mockMvc.perform(get("/vote/create").param("groupId", String.valueOf(dummyId))).andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("group",
                        hasProperty("id", is(1L))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).canUserCallVote(dummyId, sessionTestUser);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void createVoteWorksWhengroupNotSpecified() throws Exception {
        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        testGroup.setId(dummyId);
        List<Group> testPossibleGroups = new ArrayList<>();
        testPossibleGroups.add(testGroup);
        Event testVote = new Event();
        testVote.setId(dummyId);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(testPossibleGroups);
        mockMvc.perform(get("/vote/create")).andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("possibleGroups",
                        hasItem(testGroup)));
        verify(groupManagementServiceMock, times(1)).getActiveGroupsPartOf(sessionTestUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void voteCreateDoWorks() throws Exception {

        Event testVote = new Event(sessionTestUser, EventType.Vote, true);
        testVote.setId(dummyId);
        Group testGroup = new Group("Dummy Group3", new User("234345345"));

        testGroup.setId(dummyId);
        when(groupManagementServiceMock.canUserCallVote(dummyId, sessionTestUser)).thenReturn(true);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(eventManagementServiceMock.createVote(testVote)).thenReturn(testVote);
        mockMvc.perform(post("/vote/create").param("selectedGroupId", String.valueOf(dummyId))
                .sessionAttr("vote", testVote))
                .andExpect(model().attribute("eventId", is(dummyId)))
                .andExpect(model().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("vote/view"));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).canUserCallVote(dummyId, sessionTestUser);
        verify(eventManagementServiceMock, times(1)).createVote(testVote);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void viewVoteWorks() throws Exception {

        Event testVote = new Event(sessionTestUser, EventType.Vote, true);
        testVote.setId(dummyId);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(testVote);
        Map<String, Integer> testVoteResults = new HashMap<>();
        testVoteResults.put("yes", 3);
        testVoteResults.put("no", 7);
        testVoteResults.put("abstained", 3);
        testVoteResults.put("possible", 2);
        when(eventManagementServiceMock.getVoteResults(testVote)).thenReturn(testVoteResults);
        mockMvc.perform(get("/vote/view").param("eventId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("vote/view"))
                .andExpect(model().attribute("yes", is(testVoteResults.get("yes"))))
                .andExpect(model().attribute("no", is(testVoteResults.get("no"))))
                .andExpect(model().attribute("abstained", is(testVoteResults.get("abstained"))))
                .andExpect(model().attribute("possible", is(testVoteResults.get("possible"))))
                .andExpect(model().attribute("vote", hasProperty("id", is(dummyId))));
        verify(eventManagementServiceMock, times(1)).loadEvent(dummyId);
        verify(eventManagementServiceMock, times(1)).getVoteResults(testVote);
        verifyNoMoreInteractions(eventManagementServiceMock);


    }

    @Test
    public void answerVoteWorks() throws Exception {

        Event testVote = new Event(sessionTestUser, EventType.Vote, true);
        testVote.setId(dummyId);
        when(eventManagementServiceMock.loadEvent(dummyId)).thenReturn(testVote);
        when(eventLogManagementServiceMock.rsvpForEvent(testVote, sessionTestUser, EventRSVPResponse.fromString("yes")))
                .thenReturn(new EventLog());
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
