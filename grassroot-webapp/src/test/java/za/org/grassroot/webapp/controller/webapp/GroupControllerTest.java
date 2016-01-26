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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;

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

    @Before
    public void setUp() {
        setUp(groupController);


    }

    @Test
    public void viewGroupIndexWorks() throws Exception {

        Group dummyGroup = new Group();
        dummyGroup.setId(dummyId);
        Group dummySubGroup = new Group();
        dummyGroup.addMember(sessionTestUser);
        List<Group> subGroups = Arrays.asList(dummySubGroup);
        Event dummyMeeting = new Event();
        Event dummyVote = new Event();
        List<Event> dummyMeetings = Arrays.asList(dummyMeeting);
        List<Event> dummyVotes = Arrays.asList(dummyVote);
        List<Group> testGroupspartOf = new ArrayList<>();
        testGroupspartOf.add(dummyGroup);
        sessionTestUser.setGroupsPartOf(testGroupspartOf);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyGroup.getId())).thenReturn(dummyGroup);
        when(groupManagementServiceMock.hasParent(dummyGroup)).thenReturn(false);
        when(eventManagementServiceMock.getUpcomingMeetings(dummyGroup)).thenReturn(dummyMeetings);
        when(eventManagementServiceMock.getUpcomingVotes(dummyGroup)).thenReturn(dummyVotes);
        when(groupManagementServiceMock.getSubGroups(dummyGroup)).thenReturn(subGroups);
        when(groupManagementServiceMock.groupHasValidToken(dummyGroup)).thenReturn(false);
        when(groupManagementServiceMock.canUserMakeGroupInactive(sessionTestUser, dummyGroup)).thenReturn(true);
        when(groupManagementServiceMock.isGroupCreatedByUser(dummyGroup.getId(), sessionTestUser)).thenReturn(true);
        when(groupManagementServiceMock.getLastTimeGroupActive(dummyGroup)).thenReturn(LocalDateTime.now());

        mockMvc.perform(get("/group/view").param("groupId", String.valueOf(dummyGroup.getId()))).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).andExpect(model()
                .attribute("groupMeetings", hasItem(dummyMeeting))).andExpect(model().attribute("groupVotes",
                hasItem(dummyVote))).andExpect(model().attribute("subGroups", hasItem(dummySubGroup)))
                .andExpect(model().attribute("hasParent", is(false))).andExpect(model().attribute("openToken",
                is(false))).andExpect(model().attribute("canMergeWithOthers", is(true)));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyGroup.getId());
        verify(groupManagementServiceMock, times(1)).hasParent(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingMeetings(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingVotes(dummyGroup);
        verify(groupManagementServiceMock, times(1)).getSubGroups(dummyGroup);
        verify(groupManagementServiceMock, times(1)).groupHasValidToken(dummyGroup);
        verify(groupManagementServiceMock, times(1)).canUserMakeGroupInactive(sessionTestUser, dummyGroup);
        verify(groupManagementServiceMock, times(1)).isGroupCreatedByUser(dummyGroup.getId(), sessionTestUser);
        verify(groupManagementServiceMock, times(1)).getLastTimeGroupActive(dummyGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void startGroupIndexWorksWithoutParentId() throws Exception {

        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        dummyGroupCreator.addMember(sessionTestUser);
        mockMvc.perform(get("/group/create")).andExpect(view().name("group/create")).andExpect(model()
                .attribute("groupCreator", hasProperty("addedMembers", hasSize(dummyGroupCreator.getAddedMembers().size()))));
    }

    @Test
    public void startGroupIndexWorksWithParentId() throws Exception {

        Group dummyGroup = new Group();
        GroupWrapper dummyGroupCreator = new GroupWrapper(dummyGroup);
        dummyGroupCreator.addMember(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        mockMvc.perform(get("/group/create").param("parent", String.valueOf(dummyId))).
                andExpect(view().name("group/create")).andExpect(model().attribute("groupCreator",
                hasProperty("group", is(dummyGroup))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void createGroupWorks() throws Exception {
        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        Group dummyGroup = new Group(dummyGroupCreator.getGroupName(), sessionTestUser);
        dummyGroup.addMember(sessionTestUser);
        when(groupManagementServiceMock.createNewGroup(sessionTestUser, dummyGroupCreator.getGroupName()))
                .thenReturn(dummyGroup);
        when((userManagementServiceMock.loadOrSaveUser(sessionTestUser.getPhoneNumber()))).thenReturn(sessionTestUser);
        when(userManagementServiceMock.save(sessionTestUser)).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.addGroupMember(dummyGroup, sessionTestUser)).thenReturn(dummyGroup);
        mockMvc.perform(post("/group/create").sessionAttr("groupCreator", dummyGroupCreator)).andExpect(view()
                .name("redirect:view")).andExpect(model().attribute("groupId", dummyGroup.getId()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("view"));
        verify(groupManagementServiceMock, times(1)).createNewGroup(sessionTestUser, dummyGroupCreator.getGroupName());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

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
        groupCreator.addMember(sessionTestUser);
        mockMvc.perform(post("/group/create").param("addMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(view().name("group/create"));

    }

    @Test
    public void modifyGroupWorks() throws Exception {
        Group dummyGroup = new Group();
        dummyGroup.addMember(sessionTestUser);
        List<Group> testGroupPartOf = new ArrayList<>();
        testGroupPartOf.add(dummyGroup);
        sessionTestUser.setGroupsPartOf(testGroupPartOf);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        mockMvc.perform(post("/group/modify").param("group_modify", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"))
                .andExpect(model().attribute("groupModifier", instanceOf(GroupWrapper.class)));
        verify(userManagementServiceMock, times(1)).getUserById(sessionTestUser.getId());
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
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
        groupCreator.addMember(sessionTestUser);
        mockMvc.perform(post("/group/modify").param("addMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"));

    }

    @Test
    public void removeMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(sessionTestUser);
        mockMvc.perform(post("/group/modify").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/modify"));
    }

    @Test
    public void modifyGroupDoWorks() throws Exception {

        Group testGroup = new Group();
        GroupWrapper groupModifier = new GroupWrapper(testGroup);
        groupModifier.setGroupName("DummyGroup");
        testGroup.setGroupName("Dummy Group");
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);
        List<User> testUpdatedUserList = new ArrayList<>();
        List<Group> testGroupspartOf = new ArrayList<>();
        testGroupspartOf.add(testGroup);
        sessionTestUser.setGroupsPartOf(testGroupspartOf);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(groupModifier.getGroup().getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.renameGroup(testGroup, groupModifier.getGroupName())).thenReturn(testGroup);
        when(groupManagementServiceMock.addRemoveGroupMembers(testGroup, testUpdatedUserList)).thenReturn(testGroup);
        mockMvc.perform(post("/group/modify").sessionAttr("groupModifier", groupModifier)).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view")).andExpect(model()
                .attribute("groupId", is(String.valueOf(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(groupModifier.getGroup().getId());
        verify(groupManagementServiceMock, times(1)).renameGroup(testGroup, groupModifier.getGroupName());
        verify(groupManagementServiceMock, times(1)).addRemoveGroupMembers(testGroup, testUpdatedUserList);
        verify(userManagementServiceMock, times(1)).getUserById(sessionTestUser.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void newTokenWorks() throws Exception {
        Group testGroup = new Group();
        testGroup.setId(dummyId);
        List<Group> testGroupspartOf = new ArrayList<>();
        testGroupspartOf.add(testGroup);
        sessionTestUser.setGroupsPartOf(testGroupspartOf);
        testGroup.addMember(sessionTestUser);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
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
        Group testChildGroup = new Group();
        Group testParentGroup = new Group();
        testParentGroup.setId(dummyId);
        testChildGroup.setParent(testParentGroup);
        List<Group> testUsergroups = new ArrayList<>();
        testUsergroups.add(testChildGroup);
        List<Group> testPossibleParents = new ArrayList<>(testUsergroups);
        testPossibleParents.add(testParentGroup);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testChildGroup);
        when(groupManagementServiceMock.getGroupsFromUser(sessionTestUser)).thenReturn(testUsergroups);
        when(groupManagementServiceMock.isGroupAlsoParent(testChildGroup, testParentGroup)).thenReturn(true);
        mockMvc.perform(get("/group/parent").param("groupId", String.valueOf(dummyId))).andExpect(status()
                .is3xxRedirection()).andExpect(view().name("redirect:view")).andExpect(redirectedUrl("view?groupId=1"))
                .andExpect(model().attribute("groupId", is(String.valueOf(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).getGroupsFromUser(sessionTestUser);
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
        when(groupManagementServiceMock.getMergeCandidates(sessionTestUser, dummyId)).thenReturn(testCandidateGroups);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        mockMvc.perform(get("/group/consolidate/select").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view()
                .name("group/consolidate_select")).andExpect(model().attribute("group1", hasProperty("id", is(1L))))
                .andExpect(model().attribute("candidateGroups", instanceOf(List.class)));
        verify(groupManagementServiceMock, times(1)).getMergeCandidates(sessionTestUser, dummyId);
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void selectConsolidateWhenMergeCandidatesHasNoEntries() throws Exception {
        List<Group> testCandidateGroups = new ArrayList<>();
        when(groupManagementServiceMock.getMergeCandidates(sessionTestUser, dummyId)).thenReturn(testCandidateGroups);
        mockMvc.perform(get("/group/consolidate/select").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("view?groupId=1")).andExpect(view()
                .name("redirect:view")).andExpect(model().attribute("groupId", String.valueOf(dummyId)))
                .andExpect(flash().attributeExists(
                BaseController.MessageType.ERROR.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).getMergeCandidates(sessionTestUser, dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void consolidateGroupConfirmWorks() throws Exception {
        Group testGroupInto = new Group();
        testGroupInto.setId(1L);
        Group testGroupFrom = new Group();
        testGroupFrom.setId(0L);
        testGroupFrom.addMember(sessionTestUser);

        Long[] orderedIds = {1L, 0L};
        String[] orders = {"small_to_large", "2_into_1", "1_into_2"};
        when(groupManagementServiceMock.orderPairByNumberMembers(1L, 0L)).thenReturn(orderedIds);
        when(groupManagementServiceMock.loadGroup(orderedIds[0])).thenReturn(testGroupInto);
        when(groupManagementServiceMock.loadGroup(orderedIds[1])).thenReturn(testGroupFrom);
        when(groupManagementServiceMock.getGroupSize(testGroupFrom, false)).
                thenReturn(testGroupFrom.getGroupMembers().size());
        for (int i = 0; i < orders.length; i++) {
            if (i < 2) {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(0L)).param("order", orders[i])
                        .param("leaveActive", String.valueOf(true))).andExpect(model()
                        .attribute("groupInto", hasProperty("id", is(1L)))).andExpect(model()
                        .attribute("groupFrom", hasProperty("id", is(0L)))).
                        andExpect(model().attribute("numberFrom", is(testGroupFrom.getGroupMembers().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            } else {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(0L)).param("order", orders[i]).param("leaveActive",
                                String.valueOf(true))).andExpect(model()
                        .attribute("groupInto", hasProperty("id", is(0L)))).andExpect(model()
                        .attribute("groupFrom", hasProperty("id", is(1L)))).
                        andExpect(model().attribute("numberFrom", is(testGroupInto.getGroupMembers().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            }
        }
        verify(groupManagementServiceMock, times(1)).orderPairByNumberMembers(1L, 0L);
        verify(groupManagementServiceMock, times(3)).loadGroup(orderedIds[0]);
        verify(groupManagementServiceMock, times(3)).loadGroup(orderedIds[1]);
        verify(groupManagementServiceMock, times(2)).getGroupSize(testGroupFrom, false);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupInto, false);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void groupsConsolidateDoWorks() throws Exception {
        Group testGroupInto = new Group();
        testGroupInto.setId(0L);
        testGroupInto.addMember(new User());
        Group testGroupFrom = new Group();
        testGroupFrom.addMember(sessionTestUser);
        testGroupFrom.setId(1L);
        when(groupManagementServiceMock.loadGroup(testGroupFrom.getId())).thenReturn(testGroupFrom);
        when(groupManagementServiceMock.mergeGroupsSpecifyOrder(testGroupInto, testGroupFrom, !true))
                .thenReturn(testGroupInto);
        when(groupManagementServiceMock.getGroupSize(testGroupInto, false)).thenReturn(1);
        when(groupManagementServiceMock.loadGroup(testGroupInto.getId())).thenReturn(testGroupInto);
        mockMvc.perform(post("/group/consolidate/do").param("groupInto", String.valueOf(testGroupInto.getId()))
                .param("groupFrom", String.valueOf(testGroupFrom.getId())).param("leaveActive", String.valueOf(true)))
                .andExpect(model().attribute("groupId", is(String.valueOf(testGroupInto.getId()))))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("redirect:/group/view"));
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroupFrom.getId());
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroupInto.getId());
        verify(groupManagementServiceMock, times(1)).mergeGroupsSpecifyOrder(testGroupInto, testGroupFrom, false);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupInto, false);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void confirmDeleteWorks() throws Exception {
        Group group = new Group();
        group.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        when(groupManagementServiceMock.canUserMakeGroupInactive(sessionTestUser, group)).thenReturn(true);
        mockMvc.perform(get("/group/modify").param("group_delete", "").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(view().name("group/delete_confirm"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).canUserMakeGroupInactive(sessionTestUser, group);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueValid() throws Exception {
        Group group = new Group();
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        when(groupManagementServiceMock.canUserMakeGroupInactive(sessionTestUser, group)).thenReturn(true);
        when(groupManagementServiceMock.setGroupInactive(group)).thenReturn(group);
        mockMvc.perform(get("/group/delete").param("groupId", String.valueOf(dummyId)).param("confirm_field", "delete"))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home")).andExpect(flash()
                .attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).canUserMakeGroupInactive(sessionTestUser, group);
        verify(groupManagementServiceMock, times(1)).setGroupInactive(group);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueInvalid() throws Exception {

        Group group = new Group();
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        when(groupManagementServiceMock.canUserMakeGroupInactive(sessionTestUser, group)).thenReturn(true);
        when(groupManagementServiceMock.getLastTimeGroupActive(group)).thenReturn(LocalDateTime.now());
        List<Group> testGroupspartOf = new ArrayList<>();
        testGroupspartOf.add(group);
        sessionTestUser.setGroupsPartOf(testGroupspartOf);
        mockMvc.perform(get("/group/delete").param("groupId", String.valueOf(dummyId)).param("confirm_field", "d"))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model()
                        .attributeExists(BaseController.MessageType.ERROR.getMessageKey()));
        verify(userManagementServiceMock, times(1)).getUserById(sessionTestUser.getId());
        verify(groupManagementServiceMock, times(2)).canUserMakeGroupInactive(sessionTestUser, group);
        verify(groupManagementServiceMock, times(2)).loadGroup(dummyId);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void unSubscribeWorks() throws Exception {
        Group testGroup = new Group();
        testGroup.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        mockMvc.perform(get("/group/unsubscribe").param("groupId", String.valueOf(dummyId)))
                .andExpect(status().isOk()).andExpect(model().attribute("group", hasProperty("id", is(1L))))
                .andExpect(view().name("group/unsubscribe_confirm"));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void unSubgroupWorks() throws Exception {
        Group testGroup = new Group();
        testGroup.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(userManagementServiceMock.loadUser(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.isUserInGroup(testGroup, sessionTestUser)).thenReturn(true);
        when(groupManagementServiceMock.unsubscribeMember(testGroup, sessionTestUser)).thenReturn(testGroup);
        when(groupManagementServiceMock.removeGroupMember(testGroup, sessionTestUser)).thenReturn(testGroup);
        mockMvc.perform(post("/group/unsubscribe").param("groupId", String.valueOf(dummyId))
                .param("confirm_field", "unsubscribe")).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home")).andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).isUserInGroup(anyObject(), anyObject());
        verify(groupManagementServiceMock, times(1)).unsubscribeMember(testGroup, sessionTestUser);
        verify(userManagementServiceMock, times(1)).loadUser(sessionTestUser.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);

    }


}
