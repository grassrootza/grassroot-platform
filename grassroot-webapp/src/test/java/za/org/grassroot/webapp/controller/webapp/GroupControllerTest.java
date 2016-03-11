package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.BaseController;
import za.org.grassroot.webapp.model.web.GroupWrapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.hamcrest.Matchers.*;
import static org.junit.Assert.assertTrue;
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

    // todo: check for languages, check permissions, etc etc -- in effect, expand this to lots of testing, since
    // group view is the principal screen now for most group actions
    
    @Test
    public void viewGroupIndexWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group2", new User("234345345"));
        dummyGroup.setId(dummyId);

        Group dummySubGroup = new Group("Dummy Group3", new User("234345345"));

        dummyGroup.addMember(sessionTestUser);
        List<Group> subGroups = Arrays.asList(dummySubGroup);
        Event dummyMeeting = new Event();
        Event dummyVote = new Event();
        List<Event> dummyMeetings = Arrays.asList(dummyMeeting);
        List<Event> dummyVotes = Arrays.asList(dummyVote);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        when(groupManagementServiceMock.isUserInGroup(dummyGroup, sessionTestUser)).thenReturn(true);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)).thenReturn(true);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)).thenReturn(true);

        when(eventManagementServiceMock.getUpcomingMeetings(dummyGroup)).thenReturn(dummyMeetings);
        when(eventManagementServiceMock.getUpcomingVotes(dummyGroup)).thenReturn(dummyVotes);
        when(groupManagementServiceMock.getSubGroups(dummyGroup)).thenReturn(subGroups);
        when(groupManagementServiceMock.groupHasValidToken(dummyGroup)).thenReturn(false);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, dummyGroup)).thenReturn(true);
        when(groupManagementServiceMock.getLastTimeGroupActive(dummyGroup)).thenReturn(LocalDateTime.now());

        mockMvc.perform(get("/group/view").param("groupId", String.valueOf(dummyGroup.getId()))).
                andExpect(view().name("group/view")).
                andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).
                andExpect(model().attribute("groupMeetings", hasItem(dummyMeeting))).
                andExpect(model().attribute("groupVotes", hasItem(dummyVote))).
                andExpect(model().attribute("subGroups", hasItem(dummySubGroup))).
                andExpect(model().attribute("hasParent", is(false))).
                andExpect(model().attribute("openToken", is(false)));

        // todo: verify calls to isPermissionAvailable
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(eventManagementServiceMock, times(1)).getUpcomingMeetings(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingVotes(dummyGroup);
        verify(groupManagementServiceMock, times(1)).getSubGroups(dummyGroup);
        verify(groupManagementServiceMock, times(1)).groupHasValidToken(dummyGroup);
        verify(groupBrokerMock, times(1)).isDeactivationAvailable(sessionTestUser, dummyGroup);
        verifyNoMoreInteractions(eventManagementServiceMock);
    }

    @Test
    public void startGroupIndexWorksWithoutParentId() throws Exception {

        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        dummyGroupCreator.addMember(new MembershipInfo(sessionTestUser));
        mockMvc.perform(get("/group/create")).andExpect(view().name("group/create")).andExpect(model()
                .attribute("groupCreator", hasProperty("addedMembers", hasSize(dummyGroupCreator.getAddedMembers().size()))));
    }

    @Test
    public void startGroupIndexWorksWithParentId() throws Exception {
        Group dummyGroup = Group.makeEmpty();

        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(dummyGroup);
        mockMvc.perform(get("/group/create").param("parent", String.valueOf(dummyId))).
                andExpect(view().name("group/create")).andExpect(model().attribute("groupCreator",
                hasProperty("parent", is(dummyGroup))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void createGroupWorks() throws Exception {

        GroupWrapper dummyGroupCreator = new GroupWrapper();
        dummyGroupCreator.setGroupName("DummyGroup");
        MembershipInfo organizer = new MembershipInfo(sessionTestUser.getPhoneNumber(),
                                                      BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getDisplayName());
        dummyGroupCreator.addMember(organizer);
        Group dummyGroup = new Group(dummyGroupCreator.getGroupName(), sessionTestUser);
        dummyGroup.addMember(sessionTestUser);

        when(groupBrokerMock.create(sessionTestUser.getUid(), dummyGroupCreator.getGroupName(), null,
                                    new HashSet<MembershipInfo>(dummyGroupCreator.getAddedMembers()),
                                    GroupPermissionTemplate.DEFAULT_GROUP)).thenReturn(dummyGroup);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when((userManagementServiceMock.loadOrSaveUser(sessionTestUser.getPhoneNumber()))).thenReturn(sessionTestUser);
        when(userManagementServiceMock.save(sessionTestUser)).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/create").sessionAttr("groupCreator", dummyGroupCreator).
                param("groupTemplate", GroupPermissionTemplate.DEFAULT_GROUP.toString())).
                andExpect(view().name("redirect:view")).andExpect(model().attribute("groupId", dummyGroup.getId()))
                .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("view"));

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
        User user = new User("100001");
        groupCreator.addMember(new MembershipInfo(user));
        mockMvc.perform(post("/group/create").param("addMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk())
                .andExpect(view().name("group/create"));

    }

    @Test
    public void modifyGroupWorks() throws Exception {
        Group dummyGroup = new Group("Dummy Group", new User("234345345"));
        dummyGroup.addMember(sessionTestUser);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroupByUid(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(groupManagementServiceMock.isUserInGroup(dummyGroup, sessionTestUser)).thenReturn(true);

        mockMvc.perform(get("/group/change_multiple").param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/change_multiple"))
                .andExpect(model().attribute("groupModifier", instanceOf(GroupWrapper.class)));

        verify(groupManagementServiceMock, times(1)).loadGroupByUid(dummyGroup.getUid());
        verify(groupManagementServiceMock, times(1)).isUserInGroup(dummyGroup, sessionTestUser);
        verify(userManagementServiceMock, times(1)).loadUserByUid(sessionTestUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void removeMemberWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo("100001", null, ""));
        mockMvc.perform(post("/group/create").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupCreator", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/create"));
    }

    @Test
    public void addMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo(sessionTestUser));
        mockMvc.perform(post("/group/change_multiple").param("addMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/change_multiple"));

    }

    @Test
    public void addMemberModifyFails() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo(new User("100001")));
        MvcResult result = mockMvc.perform(post("/group/change_multiple").param("addMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk())
                .andExpect(model().attributeHasErrors())
                .andExpect(view().name("group/change_multiple")).andReturn();
        Map<String, Object> map = result.getModelAndView().getModel();
        Set<Map.Entry<String,Object>> entrySet =map.entrySet();
        Iterator<Map.Entry<String,Object>> iter = entrySet.iterator();
        while (iter.hasNext()){
            logger.debug(iter.next().getValue().toString());
        }
    }

    @Test
    public void removeMemberModifyWorks() throws Exception {
        GroupWrapper groupCreator = new GroupWrapper();
        groupCreator.addMember(new MembershipInfo(sessionTestUser));
        mockMvc.perform(post("/group/change_multiple").param("removeMember", String.valueOf(0)).param("removeMember", "")
                .sessionAttr("groupModifier", groupCreator))
                .andExpect(status().isOk()).andExpect(view().name("group/change_multiple"));
    }

    @Test
    public void modifyGroupDoWorks() throws Exception {

        Group testGroup = new Group("Dummy Group", new User("234345345"));
        GroupWrapper groupModifier = new GroupWrapper();
        groupModifier.populate(testGroup);
        groupModifier.setGroupName("Dummy Group");
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);
        List<User> testUpdatedUserList = new ArrayList<>();

        when(groupManagementServiceMock.loadGroupByUid(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/change_multiple").sessionAttr("groupModifier", groupModifier))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupId", is(String.valueOf(dummyId))));

        verify(groupManagementServiceMock, times(1)).loadGroupByUid(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void newTokenWorks() throws Exception {
        Group testGroup = new Group("Dummy Group", new User("234345345"));
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);

        when(groupManagementServiceMock.loadGroupByUid(testGroup.getUid())).thenReturn(testGroup);
        when(groupManagementServiceMock.groupHasValidToken(testGroup)).thenReturn(false);

        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.isUserInGroup(testGroup, sessionTestUser)).thenReturn(true);

        mockMvc.perform(post("/group/token").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));

        verify(groupManagementServiceMock, times(1)).generateGroupToken(testGroup.getUid(), sessionTestUser.getUid());
    }

    @Test
    public void closeTokenWorks() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));
        group.setId(dummyId);
        when(groupManagementServiceMock.loadGroupByUid(group.getUid())).thenReturn(group);
        when(groupManagementServiceMock.groupHasValidToken(group)).thenReturn(true);

        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.isUserInGroup(group, sessionTestUser)).thenReturn(true);

        mockMvc.perform(post("/group/token").param("groupUid", group.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));

        // note: since the model returns to group view, testing all verifications would be tedious and somewhat pointless
        verify(groupManagementServiceMock, times(1)).closeGroupToken(group.getUid(), sessionTestUser.getUid());

    }

    @Test
    public void listPossibleParentsWorks() throws Exception {
        Group testChildGroup = new Group("someGroup", new User("234345345"));

        Group testParentGroup = new Group("someParent", new User("234345345"));
        testParentGroup.setId(dummyId);
        testChildGroup.setParent(testParentGroup);
        List<Group> testUsergroups = new ArrayList<>();
        testUsergroups.add(testChildGroup);
        List<Group> testPossibleParents = new ArrayList<>(testUsergroups);
        testPossibleParents.add(testParentGroup);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testChildGroup);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(testUsergroups);
        when(groupManagementServiceMock.isGroupAlsoParent(testChildGroup, testParentGroup)).thenReturn(true);
        mockMvc.perform(get("/group/parent").param("groupId", String.valueOf(dummyId))).andExpect(status()
                .is3xxRedirection()).andExpect(view().name("redirect:view")).andExpect(redirectedUrl("view?groupId=1"))
                .andExpect(model().attribute("groupId", is(String.valueOf(dummyId))));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).getActiveGroupsPartOf(sessionTestUser);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void linkToParentWorks() throws Exception {
        Group testGroup = new Group("someGroupname", new User("234345345"));

        Group testParent = new Group("someParentGroup", new User("234345345"));
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
        List<Group> testCandidateGroups = Arrays.asList(new Group("Dummy Group", new User("234345345")));
        Group testGroup = new Group("Dummy Group2", new User("234345345"));

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
        Group testGroupInto = new Group("someGroupname", new User("234345345"));
        testGroupInto.setId(1L);

//        Group testGroupFrom = new Group();
        Group testGroupFrom = new Group("someGroupname", new User("234345345"));
        testGroupFrom.setId(0L);
        testGroupFrom.addMember(sessionTestUser);

        Long[] orderedIds = {1L, 0L};
        String[] orders = {"small_to_large", "2_into_1", "1_into_2"};
        when(groupManagementServiceMock.loadGroup(orderedIds[0])).thenReturn(testGroupInto);
        when(groupManagementServiceMock.loadGroup(orderedIds[1])).thenReturn(testGroupFrom);
        when(groupManagementServiceMock.getGroupSize(testGroupFrom.getId(), false)).thenReturn(testGroupFrom.getMembers().size());
        for (int i = 0; i < orders.length; i++) {
            if (i < 2) {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(0L)).param("order", orders[i])
                        .param("leaveActive", String.valueOf(true))).andExpect(model()
                        .attribute("groupInto", hasProperty("id", is(1L)))).andExpect(model()
                        .attribute("groupFrom", hasProperty("id", is(0L)))).
                        andExpect(model().attribute("numberFrom", is(testGroupFrom.getMembers().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            } else {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(0L)).param("order", orders[i]).param("leaveActive",
                                String.valueOf(true))).andExpect(model()
                        .attribute("groupInto", hasProperty("id", is(0L)))).andExpect(model()
                        .attribute("groupFrom", hasProperty("id", is(1L)))).
                        andExpect(model().attribute("numberFrom", is(testGroupInto.getMembers().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            }
        }
        verify(groupManagementServiceMock, times(3)).loadGroup(orderedIds[0]);
        verify(groupManagementServiceMock, times(3)).loadGroup(orderedIds[1]);
        verify(groupManagementServiceMock, times(2)).getGroupSize(testGroupFrom.getId(), false);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupInto.getId(), false);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void groupsConsolidateDoWorks() throws Exception {
        Group testGroupInto = new Group("someGroupname", new User("234345345"));

        testGroupInto.setId(0L);
        testGroupInto.addMember(new User("100001"));

        Group testGroupFrom = new Group("someGroupname2", new User("234345345"));
        testGroupFrom.addMember(sessionTestUser);
        testGroupFrom.setId(1L);
        when(groupManagementServiceMock.loadGroup(testGroupFrom.getId())).thenReturn(testGroupFrom);
        when(groupBrokerMock.merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), !true, true, false, null))
                .thenReturn(testGroupInto);
        when(groupManagementServiceMock.getGroupSize(testGroupInto.getId(), false)).thenReturn(1);
        when(groupManagementServiceMock.loadGroup(testGroupInto.getId())).thenReturn(testGroupInto);
        mockMvc.perform(post("/group/consolidate/do").param("groupInto", String.valueOf(testGroupInto.getId()))
                .param("groupFrom", String.valueOf(testGroupFrom.getId())).param("leaveActive", String.valueOf(true)))
                .andExpect(model().attribute("groupId", is(String.valueOf(testGroupInto.getId()))))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("redirect:/group/view"));
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroupFrom.getId());
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroupInto.getId());
        verify(groupBrokerMock, times(1)).merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), !true, true, false, null);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupInto.getId(), false);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueValid() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));

        when(groupManagementServiceMock.loadGroupByUid(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group)).thenReturn(true);
//        when(groupBrokerMock.deactivate(sessionTestUser.getUid(), group.getUid())).thenReturn(group);
        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "delete"))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home")).andExpect(flash()
                .attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).loadGroupByUid(group.getUid());
        verify(groupBrokerMock, times(1)).isDeactivationAvailable(sessionTestUser, group);
        verify(groupBrokerMock, times(1)).deactivate(sessionTestUser.getUid(), group.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueInvalid() throws Exception {

        Group group = new Group("someGroupname", new User("234345345"));
        group.setId(dummyId);

        when(groupManagementServiceMock.loadGroupByUid(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group)).thenReturn(true);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(group);
        when(groupManagementServiceMock.isUserInGroup(group, sessionTestUser)).thenReturn(true);

        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "d"))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attributeExists(BaseController.MessageType.ERROR.getMessageKey()));

        verify(groupManagementServiceMock, times(1)).loadGroupByUid(group.getUid());
        verify(groupBrokerMock, times(1)).isDeactivationAvailable(sessionTestUser, group);

        // redirect to view causes all view method calls, no point repeating them here, but leaving verify written & commented

    }

    @Test
    public void unSubscribeWorks() throws Exception {
        Group testGroup = new Group("Dummy Group2", new User("234345345"));
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
        Group testGroup = new Group("Dummy Group2", new User("234345345"));
        testGroup.setId(dummyId);
        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(userManagementServiceMock.loadUser(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.isUserInGroup(testGroup, sessionTestUser)).thenReturn(true);
        // when(groupManagementServiceMock.removeGroupMember(testGroup, sessionTestUser, sessionTestUser)).thenReturn(testGroup);
        mockMvc.perform(post("/group/unsubscribe").param("groupId", String.valueOf(dummyId))
                .param("confirm_field", "unsubscribe")).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home")).andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).isUserInGroup(anyObject(), anyObject());
        // verify(groupManagementServiceMock, times(1)).removeGroupMember(testGroup, sessionTestUser, sessionTestUser);
        verify(userManagementServiceMock, times(1)).loadUser(sessionTestUser.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void groupHistoryThisMonthShouldWork() throws Exception {

        Group testGroup = new Group("someGroupname", new User("234345345"));
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);

        List<Event> dummyEvents = Arrays.asList(new Event(sessionTestUser, EventType.Meeting, true),
                                                new Event(sessionTestUser, EventType.Vote, true));
        List<LogBook> dummyLogbooks = Arrays.asList(new LogBook(dummyId, "Do stuff", Timestamp.valueOf(LocalDateTime.now().plusDays(2L))),
                                                  new LogBook(dummyId, "Do more stuff", Timestamp.valueOf(LocalDateTime.now().plusDays(5L))));
        List<GroupLog> dummyGroupLogs = Arrays.asList(new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_ADDED, 0L, "guy joined"),
                                                      new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_REMOVED, 0L, "other guy left"));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getGroupEventsInPeriod(testGroup, start, end)).thenReturn(dummyEvents);
        when(logBookServiceMock.getLogBookEntriesInPeriod(dummyId, start, end)).thenReturn(dummyLogbooks);
        when(groupLogServiceMock.getLogsForGroup(testGroup, start, end)).thenReturn(dummyGroupLogs);
        when(groupManagementServiceMock.getMonthsGroupActive(testGroup)).thenReturn(dummyMonths);
        when(groupManagementServiceMock.isUserInGroup(testGroup, sessionTestUser)).thenReturn(true);

        mockMvc.perform(get("/group/history").param("groupId", String.valueOf(dummyId))).
                andExpect(view().name("group/history")).
                andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).
                andExpect(model().attribute("eventsInPeriod", is(dummyEvents))).
                andExpect(model().attribute("logBooksInPeriod", is(dummyLogbooks))).
                andExpect(model().attribute("groupLogsInPeriod", is(dummyGroupLogs))).
                andExpect(model().attribute("monthsToView", is(dummyMonths)));

        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).getMonthsGroupActive(testGroup);
        verify(groupManagementServiceMock, times(1)).isUserInGroup(testGroup, sessionTestUser);
        verifyNoMoreInteractions(groupManagementServiceMock);
//        verify(userManagementServiceMock, times(1)).getUserById(sessionTestUser.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).getGroupEventsInPeriod(testGroup, start, end);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verify(logBookServiceMock, times(1)).getLogBookEntriesInPeriod(dummyId, start, end);
        verifyNoMoreInteractions(logBookServiceMock);
        verify(groupLogServiceMock, times(1)).getLogsForGroup(testGroup, start, end);
        verifyNoMoreInteractions(groupLogServiceMock);

    }

    @Test
    public void groupHistoryLastMonthShouldWork() throws Exception {

        Group testGroup = new Group("someGroupname", new User("234345345"));

        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);

        List<Event> dummyEvents = Arrays.asList(new Event("test meeting", sessionTestUser, testGroup));
        List<LogBook> dummyLogBooks = Arrays.asList(new LogBook(dummyId, "do stuff", Timestamp.valueOf(LocalDateTime.now())));
        List<GroupLog> dummyGroupLogs = Arrays.asList(new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_ADDED, 0L));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDate lastMonth = LocalDate.now().minusMonths(1L);
        String monthToView = lastMonth.format(DateTimeFormatter.ofPattern("M-yyyy"));

        LocalDateTime start = LocalDate.parse("01-" + monthToView, DateTimeFormatter.ofPattern("dd-M-yyyy")).atStartOfDay();
        LocalDateTime end = start.plusMonths(1L);

        when(groupManagementServiceMock.loadGroup(dummyId)).thenReturn(testGroup);
        when(groupManagementServiceMock.isUserInGroup(testGroup, sessionTestUser)).thenReturn(true);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getGroupEventsInPeriod(testGroup, start, end)).thenReturn(dummyEvents);
        when(logBookServiceMock.getLogBookEntriesInPeriod(dummyId, start, end)).thenReturn(dummyLogBooks);
        when(groupLogServiceMock.getLogsForGroup(testGroup, start, end)).thenReturn(dummyGroupLogs);
        when(groupManagementServiceMock.getMonthsGroupActive(testGroup)).thenReturn(dummyMonths);

        mockMvc.perform(get("/group/history").param("groupId", String.valueOf(dummyId)).param("monthToView", monthToView)).
                andExpect(view().name("group/history")).
                andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).
                andExpect(model().attribute("eventsInPeriod", is(dummyEvents))).
                andExpect(model().attribute("logBooksInPeriod", is(dummyLogBooks))).
                andExpect(model().attribute("groupLogsInPeriod", is(dummyGroupLogs))).
                andExpect(model().attribute("monthsToView", is(dummyMonths)));

        verify(groupManagementServiceMock, times(1)).loadGroup(dummyId);
        verify(groupManagementServiceMock, times(1)).isUserInGroup(testGroup, sessionTestUser);
        verify(groupManagementServiceMock, times(1)).getMonthsGroupActive(testGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);
//        verify(userManagementServiceMock, times(1)).getUserById(sessionTestUser.getId());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(eventManagementServiceMock, times(1)).getGroupEventsInPeriod(testGroup, start, end);
        verifyNoMoreInteractions(eventManagementServiceMock);
        verify(logBookServiceMock, times(1)).getLogBookEntriesInPeriod(dummyId, start, end);
        verifyNoMoreInteractions(logBookServiceMock);
        verify(groupLogServiceMock, times(1)).getLogsForGroup(testGroup, start, end);
        verifyNoMoreInteractions(groupLogServiceMock);

    }

    @Test
    public void addBulkMembersDoShouldWork() throws Exception{
        String testNumbers = "0616780986,0833403013,01273,0799814669";
        List<String> numbers_to_be_added = new ArrayList<>();
        numbers_to_be_added.add("27616780986");
        numbers_to_be_added.add("27833403013");
        numbers_to_be_added.add("27799814669");

        Set<MembershipInfo> testMembers = Sets.newHashSet(
                new MembershipInfo("27616780986", BaseRoles.ROLE_ORDINARY_MEMBER, null),
                new MembershipInfo("27833403013", BaseRoles.ROLE_ORDINARY_MEMBER, null),
                new MembershipInfo("27799814669", BaseRoles.ROLE_ORDINARY_MEMBER, null));

        Group testGroup = new Group("someGroupName", new User("27616780989"));
        when(groupManagementServiceMock.loadGroupByUid(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/add_bulk").param("groupUid", String.valueOf(testGroup.getUid())).param("list",testNumbers))
                .andExpect(status().isOk()).andExpect(view().name("group/add_bulk_error"));
        verify(groupManagementServiceMock,times(1)).loadGroupByUid(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(sessionTestUser.getUid(), testGroup.getUid(), testMembers);
      //  verifyNoMoreInteractions(userManagementServiceMock);

    }

}
