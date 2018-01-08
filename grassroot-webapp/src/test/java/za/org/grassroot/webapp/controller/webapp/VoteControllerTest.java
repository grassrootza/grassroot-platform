package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.task.Vote;
import za.org.grassroot.services.task.VoteBroker;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.VoteWrapper;

import java.time.Instant;
import java.time.LocalDateTime;
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

    @Mock
    private VoteBroker voteBrokerMock;

    @InjectMocks
    private VoteController voteController;

    @Before
    public void setUp() {
        setUp(voteController);
    }

    @Test
    public void createVoteWorksWhenGroupIdSpecified() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);

        when(permissionBrokerMock.countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(1);

        mockMvc.perform(get("/vote/create").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("group", hasProperty("uid", is(testGroup.getUid()))));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verify(permissionBrokerMock, times(1)).validateGroupPermission(sessionTestUser, testGroup, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

    @Test
    public void createVoteWorksWhengroupNotSpecified() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        List<Group> testPossibleGroups = Collections.singletonList(testGroup);

        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(1);
        when(permissionBrokerMock.getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE)).thenReturn(testPossibleGroups);

        mockMvc.perform(get("/vote/create"))
                .andExpect(status().isOk())
                .andExpect(view().name("vote/create"))
                .andExpect(model().attribute("possibleGroups", hasItem(testGroup)));

        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verify(permissionBrokerMock, times(1)).getActiveGroupsSorted(sessionTestUser, Permission.GROUP_PERMISSION_CREATE_GROUP_VOTE);
        verifyNoMoreInteractions(permissionBrokerMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void voteCreateDoWorks() throws Exception {

        Group testGroup = new Group("Dummy Group3", new User("234345345", null, null));
        LocalDateTime testTime = LocalDateTime.now().plusMinutes(7L);
        VoteWrapper testVote = VoteWrapper.makeEmpty();
        testVote.setTitle("test vote");
        testVote.setEventDateTime(testTime);
        testVote.setDescription("Abracadabra");

        mockMvc.perform(post("/vote/create").contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("selectedGroupUid", testGroup.getUid())
                .sessionAttr("vote", testVote))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/group/view"))
                .andExpect(redirectedUrl("/group/view?groupUid=" + testGroup.getUid()));

        verify(eventBrokerMock, times(1)).createVote(sessionTestUser.getUid(), testGroup.getUid(), JpaEntityType.GROUP, "test vote",
                                                     testTime, false, "Abracadabra", Collections.emptySet(), null);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
    }

    @Test
    public void viewVoteWorks() throws Exception {
        Group testGroup = new Group("tg1", sessionTestUser);
        List<User> testUsers = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            testUsers.add(new User("050111000" + i, null, null));
        }

        testUsers.forEach(usr -> testGroup.addMember(usr, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null));

        Vote testVote = new Vote("test", Instant.now(), sessionTestUser, testGroup);
        Map<String, Long> testVoteResults = new LinkedHashMap<>();
        testVoteResults.put("yes", 5L);
        testVoteResults.put("no", 10L);
        testVoteResults.put("abstain", 7L);
        when(voteBrokerMock.load(testVote.getUid())).thenReturn(testVote);
        when(voteBrokerMock.fetchVoteResults(sessionTestUser.getUid(), testVote.getUid(), false)).thenReturn(testVoteResults);

        mockMvc.perform(get("/vote/view").param("eventUid", testVote.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("vote/view"))
                .andExpect(model().attribute("voteTotals", is(testVoteResults)))
                .andExpect(model().attribute("possible", is((long) testGroup.getMembers().size())))
                .andExpect(model().attribute("vote", hasProperty("uid", is(testVote.getUid()))));

        verify(voteBrokerMock, times(1)).load(testVote.getUid());
        verify(voteBrokerMock, times(1)).fetchVoteResults(sessionTestUser.getUid(), testVote.getUid(), false);
        verifyNoMoreInteractions(voteBrokerMock);
    }

    @Test
    public void answerVoteWorks() throws Exception {

        Vote testVote = new Vote("test", Instant.now(), sessionTestUser, new Group("tg1", sessionTestUser));

        when(voteBrokerMock.load(testVote.getUid())).thenReturn(testVote);

        mockMvc.perform(get("/vote/answer").header("referer", "vote").param("eventUid", testVote.getUid()).param("answer", "yes"))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/vote/view"))
                .andExpect(flash().attributeExists(BaseController.MessageType.INFO.getMessageKey()))
                .andExpect(redirectedUrl("/vote/view?eventUid=" + testVote.getUid()));

        verify(voteBrokerMock, times(1)).load(testVote.getUid());
        verify(voteBrokerMock, times(1)).recordUserVote(sessionTestUser.getUid(), testVote.getUid(), "yes");
        verifyNoMoreInteractions(eventBrokerMock);
        verifyNoMoreInteractions(eventLogBrokerMock);
    }


}
