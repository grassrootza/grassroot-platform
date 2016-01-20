package za.org.grassroot.webapp.controller.webapp;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


/**
 * Created by paballo on 2016/01/19.
 */
public class GroupControllerTest extends WebAppAbstractUnitTest {

    private static final Logger logger = LoggerFactory.getLogger(MeetingControllerTest.class);
    private static final Long dummyId = 1L;


    @InjectMocks
    private GroupController groupController;

    ;


    @Before
    public void setUp() {
        setUp(groupController);


    }

    @Test
    public void viewGroupIndexWorks() throws Exception {

        User testUser = new User();
        Group dummyGroup = new Group("", testUser);
        dummyGroup.setId(dummyId);
        Group dummySubGroup = new Group();
        List<Group> subGroups = new ArrayList<>();
        subGroups.add(dummySubGroup);
        dummySubGroup.setParent(dummyGroup);
        Event dummyMeeting = new Event();
        Event dummyVote = new Event();
        List<Event> dummyMeetings = new ArrayList<>();
        dummyMeetings.add(dummyMeeting);
        List<Event> dummyVotes = new ArrayList<>();
        dummyVotes.add(dummyVote);
        when(groupManagementServiceMock.loadGroup(dummyGroup.getId())).thenReturn(dummyGroup);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasParent(dummyGroup)).thenReturn(false);
        when(eventManagementServiceMock.getUpcomingMeetings(dummyGroup)).thenReturn(dummyMeetings);
        when(eventManagementServiceMock.getUpcomingVotes(dummyGroup)).thenReturn(dummyVotes);
        when(groupManagementServiceMock.getSubGroups(dummyGroup)).thenReturn(subGroups);
        when(groupManagementServiceMock.groupHasValidToken(dummyGroup)).thenReturn(false);
        when(groupManagementServiceMock.canUserMakeGroupInactive(testUser, dummyGroup)).thenReturn(true);
        when(groupManagementServiceMock.isGroupCreatedByUser(dummyGroup.getId(), testUser)).thenReturn(true);
        mockMvc.perform(get("/group/view").param("groupId", String.valueOf(dummyGroup.getId()))).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).andExpect(model()
                .attribute("groupMeetings", hasItem(dummyMeeting))).andExpect(model().attribute("groupVotes",
                hasItem(dummyVote))).andExpect(model().attribute("subGroups", hasItem(dummySubGroup)))
                .andExpect(model().attribute("hasParent", is(false))).andExpect(model().attribute("openToken",
                is(false))).andExpect(model().attribute("canMergeWithOthers", is(true)));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyGroup.getId());
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).hasParent(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingMeetings(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingVotes(dummyGroup);
        verify(groupManagementServiceMock, times(1)).getSubGroups(dummyGroup);
        verify(groupManagementServiceMock, times(1)).groupHasValidToken(dummyGroup);
        verify(groupManagementServiceMock, times(1)).canUserMakeGroupInactive(testUser, dummyGroup);
        verify(groupManagementServiceMock, times(1)).isGroupCreatedByUser(dummyGroup.getId(), testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void startGroupIndexWorksWithoutParentId() throws Exception {

        User testUser = new User();
        testUser.setPhoneNumber("8888888888");
        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        dummyGroupCreator.addMember(testUser);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get("/group/create")).andExpect(view().name("group/create")).andExpect(model()
                .attribute("groupCreator", hasProperty("addedMembers", hasSize(dummyGroupCreator.getAddedMembers().size()))));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void startGroupIndexWorksWithParentId() throws Exception {

        User testUser = new User();
        testUser.setPhoneNumber(testUserPhone);
        Group dummyGroup = new Group();
        GroupWrapper dummyGroupCreator = new GroupWrapper(dummyGroup);
        dummyGroupCreator.addMember(testUser);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(dummyGroup.getId())).thenReturn(dummyGroup);
        mockMvc.perform(get("/group/create").param("parentId", String.valueOf(dummyId))).
                andExpect(view().name("group/create")).andExpect(model().attribute("groupCreator",
                hasProperty("group", is(dummyGroup))));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void createGroupWorks() throws Exception {


        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");

        User testUser = new User();
        testUser.setPhoneNumber(testUserPhone);
        Group dummyGroup = new Group(dummyGroupCreator.getGroupName(), testUser);
        dummyGroup.addMember(testUser);
        when(userManagementServiceMock.fetchUserByUsername(testUser.getPhoneNumber())).thenReturn(testUser);
        when(groupManagementServiceMock.createNewGroup(testUser, dummyGroupCreator.getGroupName()))
                .thenReturn(dummyGroup);
        when((userManagementServiceMock.loadOrSaveUser(testUser.getPhoneNumber()))).thenReturn(testUser);
        when(userManagementServiceMock.save(testUser)).thenReturn(testUser);
        when(groupManagementServiceMock.addGroupMember(dummyGroup, testUser)).thenReturn(dummyGroup);
        mockMvc.perform(post("/group/create").sessionAttr("groupCreator", dummyGroupCreator)).andExpect(view()
                .name("redirect:view")).andExpect(model().attribute("groupId", dummyGroup.getId()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("view"));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUser.getPhoneNumber());
        verify(groupManagementServiceMock, times(1)).createNewGroup(testUser, dummyGroupCreator.getGroupName());

        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }
   /* @Test
    public void createGroupFailsValidation() throws Exception{

      GroupWrapper dummyGroupCreator = new GroupWrapper();
      mockMvc.perform(post("/group/create").contentType(MediaType.APPLICATION_FORM_URLENCODED).param("groupName", "")
              .sessionAttr("groupCreator", dummyGroupCreator)).andExpect(view().name("group/create"))
              .andExpect(model().attribute("groupCreator", is(dummyGroupCreator)));

    }*/


    @Test
    public void addMemberWorks() throws Exception {

        GroupWrapper groupCreator = new GroupWrapper();
        mockMvc.perform(post("/group/create").param("addMember", "").sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(model().attribute("groupCreator", instanceOf(GroupWrapper.class)))
                .andExpect(view().name("group/create"));


    }

    @Test
    public void addMemberFailsValidation() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        User testUser = new User();
        testUser.setDisplayName("Test User");
        groupCreator.addMember(testUser);


        mockMvc.perform(post("/group/create").param("addMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(view().name("group/create"));

    }

    @Test
    public void modifyGroupWorks() throws Exception {

        Group dummyGroup = new Group();
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        mockMvc.perform(post("/group/modify").param("group_modify", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"))
                .andExpect(model().attribute("groupModifier", instanceOf(GroupWrapper.class)));
    }

    @Test
    public void removeMemberWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new User());
        mockMvc.perform(post("/group/create").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/create"));
    }

    @Test
    public void addMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new User());
        mockMvc.perform(post("/group/modify").param("addMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"));

    }

    @Test
    public void removeMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new User());
        mockMvc.perform(post("/group/modify").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"));

    }

    @Test
    public void modifyGroupDoWorks() throws Exception {
        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.setGroupName("DummyGroup");
        Group testGroup = new Group();
        testGroup.setGroupName("Dummy Group");
        testGroup.setId(dummyId);
        User testUser = new User();
        //  groupModifier.addMember(testUser);
        List<User> testUpdatedUserList = new ArrayList<>();
        when(groupManagementServiceMock.loadGroup(groupModifier.getGroup().getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.renameGroup(testGroup, groupModifier.getGroupName())).thenReturn(testGroup);
        when(userManagementServiceMock.loadOrSaveUser(testUser)).thenReturn(testUser);
        when(groupManagementServiceMock.addRemoveGroupMembers(testGroup, testUpdatedUserList)).thenReturn(testGroup);
        mockMvc.perform(post("/group/modify").sessionAttr("groupModifier", groupModifier)).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view")).andExpect(model()
                .attribute("groupId", is(String.valueOf(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(groupModifier.getGroup().getId());
        verify(groupManagementServiceMock, times(1)).renameGroup(testGroup, groupModifier.getGroupName());
        //  verify(userManagementServiceMock,times(1)).loadOrSaveUser(testUser);
        verify(groupManagementServiceMock, times(1)).addRemoveGroupMembers(testGroup, testUpdatedUserList);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void newTokenWorks() throws Exception {
        Group group = new Group();
        group.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        mockMvc.perform(post("/group/modify").param("token_create", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/new_token"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void extendTokenWorks() throws Exception {
        Group group = new Group();
        group.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        mockMvc.perform(post("/group/modify").param("token_extend", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/extend_token"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void cancelTokenWorks() throws Exception {
        Group group = new Group();
        group.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        mockMvc.perform(post("/group/modify").param("token_cancel", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/close_token"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void createGroupTokenWorks() throws Exception {
        String[] actions = {"create", "extend", "close"};
        Group testGroup = new Group();
        testGroup.setTokenExpiryDateTime(Timestamp.from(Instant.now()));
        Integer days = 5;
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(groupManagementServiceMock.extendGroupToken(testGroup, days)).thenReturn(testGroup);
        when(groupManagementServiceMock.generateGroupToken(testGroup, days)).thenReturn(testGroup);
        when(groupManagementServiceMock.invalidateGroupToken(testGroup)).thenReturn(testGroup);
        for (String action : actions) {
            mockMvc.perform(post("/group/token").param("groupId", String.valueOf(dummyId))
                    .param("action", action).param("days", String.valueOf(days))).andExpect(status().is3xxRedirection())
                    .andExpect(view().name("redirect:/group/view")).andExpect(redirectedUrl("/group/view"));
        }
        verify(groupManagementServiceMock, times(3)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).extendGroupToken(testGroup, days);
        verify(groupManagementServiceMock, times(1)).generateGroupToken(testGroup, days);
        verify(groupManagementServiceMock, times(1)).invalidateGroupToken(testGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void requestGroupLanguageWorks() throws Exception {
        Group testGroup = new Group();
        testGroup.setId(dummyId);
        LinkedHashMap<String, String> testImplementedLanguages = new LinkedHashMap<>();
        testImplementedLanguages.put("English", "en");
        List<Map.Entry<String, String>> languages = new ArrayList<>(testImplementedLanguages.entrySet());
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(userManagementServiceMock.getImplementedLanguages()).thenReturn(testImplementedLanguages);
        mockMvc.perform(get("/group/modify").param("group_language", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(model().attribute("group", hasProperty("id", is(1L)))).andExpect(model().attribute("languages"
                , instanceOf(List.class)));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(userManagementServiceMock, times(1)).getImplementedLanguages();
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void listPossibleParentsWorks() throws Exception {
        User testuser = new User();
        Group testChildGroup = new Group();
        Group testParentGroup = new Group();
        testParentGroup.setId(dummyId);
        testChildGroup.setParent(testParentGroup);
        List<Group> testUsergroups = new ArrayList<>();
        testUsergroups.add(testChildGroup);
        List<Group> testPossibleParents = new ArrayList<>(testUsergroups);
        testPossibleParents.add(testParentGroup);

        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testuser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testChildGroup);
        when(groupManagementServiceMock.getGroupsFromUser(testuser)).thenReturn(testUsergroups);
        when(groupManagementServiceMock.isGroupAlsoParent(testChildGroup, testParentGroup)).thenReturn(true);
        mockMvc.perform(get("/group/parent").param("groupId", String.valueOf(dummyId))).andExpect(status()
                .is3xxRedirection()).andExpect(view().name("redirect:view")).andExpect(redirectedUrl("view?groupId=1"))
                .andExpect(model().attribute("groupId", is(String.valueOf(dummyId))));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).getGroupsFromUser(testuser);
        // verify(groupManagementServiceMock,times(1)).isGroupAlsoParent(testChildGroup,testParentGroup);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void linkToParentWorks() throws Exception {
        Group testGroup = new Group();
        Group testParent = new Group();
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroup(0L)).thenReturn(testParent);
        when(groupManagementServiceMock.linkSubGroup(testGroup, testParent)).thenReturn(testGroup);
        mockMvc.perform(post("/group/link").param("groupId", String.valueOf(dummyId)).param("parentId", String.valueOf(0L)))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:view")).
                andExpect(redirectedUrl("view?groupId=1")).andExpect(model()
                .attribute("groupId", String.valueOf(dummyId))).andExpect(flash()
                .attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).loadGroup(0L);
        verify(groupManagementServiceMock, times(1)).linkSubGroup(testGroup, testParent);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void selectConsolidateWorksWhenMergeCandidateHasEntries() throws Exception {
        List<Group> testCandidateGroups = Arrays.asList(new Group());
        Group testGroup = new Group();
        testGroup.setId(dummyId);
        User testUser = new User();
        when(groupManagementServiceMock.getMergeCandidates(testUser, dummyId)).thenReturn(testCandidateGroups);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        mockMvc.perform(get("/group/consolidate/select").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view()
                .name("group/consolidate_select")).andExpect(model().attribute("group1", hasProperty("id", is(1L))))
                .andExpect(model().attribute("candidateGroups", instanceOf(List.class)));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).getMergeCandidates(testUser, dummyId);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void selectConsolidateWhenMergeCandidatesHasNoEntries() throws Exception {
        List<Group> testCandidateGroups = new ArrayList<>();
        User testUser = new User();
        when(groupManagementServiceMock.getMergeCandidates(testUser, dummyId)).thenReturn(testCandidateGroups);
        when(userManagementServiceMock.fetchUserByUsername(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get("/group/consolidate/select").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("view?groupId=1")).andExpect(view()
                .name("redirect:view")).andExpect(model().attribute("groupId", String.valueOf(dummyId))).andExpect(flash().attributeExists(
                BaseController.MessageType.ERROR.getMessageKey()));
        verify(userManagementServiceMock, times(1)).fetchUserByUsername(testUserPhone);
        verify(groupManagementServiceMock, times(1)).getMergeCandidates(testUser, dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    /*  @Test
      public void consolidateGroupConfirmWorks() throws Exception{
          Group testGroupInto = new Group();
          testGroupInto.setId(0L);
          Group testGroupFrom = new Group();
          testGroupFrom.setId(1L);
          Long[] orderedIds ={1L,0L};
          String[] orders ={"small_to_large","2_into_1","1_into_2"};
          when(groupManagementServiceMock.orderPairByNumberMembers(0L,1L)).thenReturn(orderedIds);
          when(groupManagementServiceMock.loadGroup(orderedIds[0])).thenReturn(testGroupInto);
          when(groupManagementServiceMock.loadGroup(orderedIds[1])).thenReturn(testGroupFrom);
          for(String order:orders){
              mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                      .param("groupId2", String.valueOf(0L)).param("order", order)).andExpect(model()
                      .attribute("groupInto", hasProperty("id", is(1L)))).andExpect(model()
                      .attribute("groupFrom", hasProperty("id", is(0L))));
          }

      }*/
    @Test
    public void groupsConsolidateDoWorks() throws Exception {


    }

}
