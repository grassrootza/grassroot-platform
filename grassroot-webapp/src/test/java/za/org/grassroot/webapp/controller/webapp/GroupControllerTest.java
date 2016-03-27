package za.org.grassroot.webapp.controller.webapp;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.MvcResult;
import za.org.grassroot.core.domain.*;
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
        List<Group> subGroups = Collections.singletonList(dummySubGroup);
//        Event dummyMeeting = new Event();
//        Event dummyVote = new Event();
        // todo: new design?
        Meeting dummyMeeting = null;
        Vote dummyVote = null;
        List<Meeting> dummyMeetings = Collections.singletonList(dummyMeeting);
        List<Vote> dummyVotes = Collections.singletonList(dummyVote);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)).thenReturn(true);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup,
                                                        Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)).thenReturn(true);

        when(eventManagementServiceMock.getUpcomingMeetings(dummyGroup)).thenReturn(dummyMeetings);
        when(eventManagementServiceMock.getUpcomingVotes(dummyGroup)).thenReturn(dummyVotes);
        when(groupManagementServiceMock.getSubGroups(dummyGroup)).thenReturn(subGroups);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, dummyGroup, true)).thenReturn(true);
        when(groupManagementServiceMock.getLastTimeGroupActive(dummyGroup)).thenReturn(LocalDateTime.now());

        mockMvc.perform(get("/group/view").param("groupUid", dummyGroup.getUid())).
                andExpect(view().name("group/view")).
                andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).
                andExpect(model().attribute("groupMeetings", hasItem(dummyMeeting))).
                andExpect(model().attribute("groupVotes", hasItem(dummyVote))).
                andExpect(model().attribute("subGroups", hasItem(dummySubGroup))).
                andExpect(model().attribute("hasParent", is(false))).
                andExpect(model().attribute("openToken", is(false)));

        // todo: verify sequence of permission checking calls
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verify(eventManagementServiceMock, times(1)).getUpcomingMeetings(dummyGroup);
        verify(eventManagementServiceMock, times(1)).getUpcomingVotes(dummyGroup);
        verify(groupManagementServiceMock, times(1)).getSubGroups(dummyGroup);
        // verify(groupBrokerMock, times(1)).isDeactivationAvailable(sessionTestUser, dummyGroup, true);
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

        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        mockMvc.perform(get("/group/create").param("parent", dummyGroup.getUid())).
                andExpect(view().name("group/create")).andExpect(model().attribute("groupCreator",
                hasProperty("parent", is(dummyGroup))));
        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
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
                                    new HashSet<>(dummyGroupCreator.getAddedMembers()),
                                    GroupPermissionTemplate.DEFAULT_GROUP, null)).thenReturn(dummyGroup);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when((userManagementServiceMock.loadOrSaveUser(sessionTestUser.getPhoneNumber()))).thenReturn(sessionTestUser);
        when(userManagementServiceMock.save(sessionTestUser)).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/create").sessionAttr("groupCreator", dummyGroupCreator).
                param("groupTemplate", GroupPermissionTemplate.DEFAULT_GROUP.toString()))
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupUid", dummyGroup.getUid()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("view?groupUid=" + dummyGroup.getUid()));

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
        when(groupBrokerMock.load(dummyGroup.getUid())).thenReturn(dummyGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, dummyGroup, null)).thenReturn(true);

        mockMvc.perform(get("/group/change_multiple").param("groupUid", dummyGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/change_multiple"))
                .andExpect(model().attribute("groupModifier", instanceOf(GroupWrapper.class)));

        verify(groupBrokerMock, times(1)).load(dummyGroup.getUid());
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, dummyGroup, null);
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

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/change_multiple").sessionAttr("groupModifier", groupModifier))
                .andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:view"))
                .andExpect(model().attribute("groupUid", is(testGroup.getUid())));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void newTokenWorks() throws Exception {
        Group testGroup = new Group("Dummy Group", new User("234345345"));
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup, null)).thenReturn(true);

        mockMvc.perform(post("/group/token").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk())
                .andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));

        verify(groupBrokerMock, times(1)).openJoinToken(sessionTestUser.getUid(), testGroup.getUid(), false, null);
    }

    @Test
    public void closeTokenWorks() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));
        group.setId(dummyId);
        when(groupBrokerMock.load(group.getUid())).thenReturn(group);

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, group, null)).thenReturn(true);

        mockMvc.perform(post("/group/token").param("groupUid", group.getUid()))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attribute("group", hasProperty("id", is(dummyId))));

        // note: since the model returns to group view, testing all verifications would be tedious and somewhat pointless
        verify(groupBrokerMock, times(1)).closeJoinToken(sessionTestUser.getUid(), group.getUid());

    }

    @Test
    public void listPossibleParentsWorks() throws Exception {
        Group testChildGroup = new Group("someGroup", new User("234345345"));

        Group testParentGroup = new Group("someParent", new User("234345345"));
        testParentGroup.setId(dummyId);
        testChildGroup.setParent(testParentGroup);
        Set<Group> testUsergroups = new HashSet<>();
        testUsergroups.add(testChildGroup);
        List<Group> testPossibleParents = new ArrayList<>(testUsergroups);
        testPossibleParents.add(testParentGroup);
        when(groupBrokerMock.load(testChildGroup.getUid())).thenReturn(testChildGroup);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(testUsergroups);
        when(groupManagementServiceMock.isGroupAlsoParent(testChildGroup, testParentGroup)).thenReturn(true);
        mockMvc.perform(get("/group/parent").param("groupId", String.valueOf(dummyId))).andExpect(status()
                .is3xxRedirection()).andExpect(view().name("redirect:view")).andExpect(redirectedUrl("view?groupId=1"))
                .andExpect(model().attribute("groupId", is(String.valueOf(dummyId))));
        verify(permissionBrokerMock, times(1)).getActiveGroups(sessionTestUser, null);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void linkToParentWorks() throws Exception {
        Group testGroup = new Group("someGroupname", new User("234345345"));

        Group testParent = new Group("someParentGroup", new User("234345345"));
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(testParent.getUid())).thenReturn(testParent);
        when(groupManagementServiceMock.linkSubGroup(testGroup, testParent)).thenReturn(testGroup);
        mockMvc.perform(post("/group/link").param("groupUid", testGroup.getUid()).param("parentUid", testParent.getUid()))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:view"))
                .andExpect(redirectedUrl("view?groupUid=" + testGroup.getUid()))
                .andExpect(model().attribute("groupUid", testGroup.getUid()))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(groupBrokerMock, times(1)).load(testParent.getUid());
        verify(groupManagementServiceMock, times(1)).linkSubGroup(testGroup, testParent);
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void selectConsolidateWorksWhenMergeCandidateHasEntries() throws Exception {
        List<Group> testCandidateGroups = Arrays.asList(new Group("Dummy Group", new User("234345345")));
        Group testGroup = new Group("Dummy Group2", new User("234345345"));

        testGroup.setId(dummyId);
        when(groupManagementServiceMock.getMergeCandidates(sessionTestUser, dummyId)).thenReturn(testCandidateGroups);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get("/group/consolidate/select").param("groupUid", testGroup.getUid()))
                .andExpect(status().isOk()).andExpect(view()
                .name("group/consolidate_select")).andExpect(model().attribute("group1", hasProperty("id", is(1L))))
                .andExpect(model().attribute("candidateGroups", instanceOf(List.class)));
        verify(groupManagementServiceMock, times(1)).getMergeCandidates(sessionTestUser, dummyId);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
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
        Group testGroupSmall = new Group("someGroupname", new User("234345345"));
        testGroupSmall.setId(1L);

        Group testGroupLarge = new Group("someGroupname", new User("234345345"));
        testGroupLarge.setId(2L);
        testGroupLarge.addMember(sessionTestUser);

        String[] orderedUids = {testGroupSmall.getUid(), testGroupLarge.getUid()};
        String[] orders = {"small_to_large", "1_into_2", "2_into_1"};
        when(groupBrokerMock.load(orderedUids[0])).thenReturn(testGroupSmall);
        when(groupBrokerMock.load(orderedUids[1])).thenReturn(testGroupLarge);

        when(groupManagementServiceMock.getGroupSize(testGroupLarge.getId(), false)).thenReturn(testGroupLarge.getMembers().size());
        for (int i = 0; i < orders.length; i++) {
            if (i < 2) {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(2L)).param("order", orders[i])
                        .param("leaveActive", String.valueOf(true)))
                        .andExpect(model().attribute("groupInto", hasProperty("id", is(2L))))
                        .andExpect(model().attribute("groupFrom", hasProperty("id", is(1L))))
                        .andExpect(model().attribute("numberFrom", is(testGroupSmall.getMembers().size())))
                        .andExpect(model().attribute("leaveActive", is(true)));
            } else {
                mockMvc.perform(post("/group/consolidate/confirm").param("groupId1", String.valueOf(1L))
                        .param("groupId2", String.valueOf(2L)).param("order", orders[i]).param("leaveActive", "true")).
                        andExpect(model().attribute("groupInto", hasProperty("id", is(1L)))).
                        andExpect(model().attribute("groupFrom", hasProperty("id", is(2L)))).
                        andExpect(model().attribute("numberFrom", is(testGroupLarge.getMembers().size()))).
                        andExpect(model().attribute("leaveActive", is(true)));
            }
        }
        verify(groupBrokerMock, times(3)).load(orderedUids[0]);
        verify(groupBrokerMock, times(3)).load(orderedUids[1]);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupLarge.getId(), false);
        verify(groupManagementServiceMock, times(2)).getGroupSize(testGroupSmall.getId(), false);
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

        when(groupBrokerMock.load(testGroupFrom.getUid())).thenReturn(testGroupFrom);
        when(groupBrokerMock.merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), true, true, false, null))
                .thenReturn(testGroupInto);
        when(groupManagementServiceMock.getGroupSize(testGroupInto.getId(), false)).thenReturn(1);
        when(groupBrokerMock.load(testGroupInto.getUid())).thenReturn(testGroupInto);

        mockMvc.perform(post("/group/consolidate/do").param("groupInto", String.valueOf(testGroupInto.getId()))
                .param("groupFrom", String.valueOf(testGroupFrom.getId())).param("leaveActive", "true").param("confirm_field", "merge"))
                .andExpect(model().attribute("groupId", is(String.valueOf(testGroupInto.getId()))))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()))
                .andExpect(view().name("redirect:/group/view"));
        verify(groupBrokerMock, times(1)).load(testGroupFrom.getUid());
        verify(groupBrokerMock, times(1)).load(testGroupInto.getUid());
        verify(groupBrokerMock, times(1)).merge(sessionTestUser.getUid(), testGroupInto.getUid(), testGroupFrom.getUid(), true, true, false, null);
        verify(groupManagementServiceMock, times(1)).getGroupSize(testGroupInto.getId(), false);
        verifyNoMoreInteractions(groupManagementServiceMock);


    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueValid() throws Exception {
        Group group = new Group("someGroupname", new User("234345345"));

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group, true)).thenReturn(true);
//        when(groupBrokerMock.deactivate(sessionTestUser.getUid(), group.getUid())).thenReturn(group);
        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "delete"))
                .andExpect(status().is3xxRedirection()).andExpect(view().name("redirect:/home"))
                .andExpect(redirectedUrl("/home")).andExpect(flash()
                .attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupBrokerMock, times(1)).load(group.getUid());
        verify(groupBrokerMock, times(1)).deactivate(sessionTestUser.getUid(), group.getUid(), true);
        verifyNoMoreInteractions(groupManagementServiceMock);
    }

    @Test
    public void deleteGroupWorksWithConfirmFieldValueInvalid() throws Exception {

        Group group = new Group("someGroupname", new User("234345345"));
        group.setId(dummyId);

        when(groupBrokerMock.load(group.getUid())).thenReturn(group);
        when(groupBrokerMock.isDeactivationAvailable(sessionTestUser, group, true)).thenReturn(true);

        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, group, null)).thenReturn(true);

        mockMvc.perform(post("/group/inactive").param("groupUid", group.getUid()).param("confirm_field", "d"))
                .andExpect(status().isOk()).andExpect(view().name("group/view"))
                .andExpect(model().attributeExists(BaseController.MessageType.ERROR.getMessageKey()));

        verify(groupBrokerMock, times(1)).load(group.getUid());
        verifyZeroInteractions(groupBrokerMock);

        // redirect to view causes all view method calls, no point repeating them here, but leaving verify written & commented

    }

    // todo: add checks for security exceptions if user not a member of group
    @Test
    public void unSubgroupWorks() throws Exception {
        Group testGroup = new Group("Dummy Group2", new User("234345345"));
        testGroup.setId(dummyId);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.loadUser(sessionTestUser.getId())).thenReturn(sessionTestUser);

        mockMvc.perform(post("/group/unsubscribe").param("groupId", String.valueOf(dummyId))
                .param("confirm_field", "unsubscribe")).andExpect(status().is3xxRedirection())
                .andExpect(view().name("redirect:/home")).andExpect(redirectedUrl("/home"))
                .andExpect(flash().attributeExists(BaseController.MessageType.SUCCESS.getMessageKey()));
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(userManagementServiceMock, times(1)).loadUser(sessionTestUser.getId());
        verify(groupBrokerMock, times(1)).unsubscribeMember(sessionTestUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);

    }

    @Test
    public void groupHistoryThisMonthShouldWork() throws Exception {

        Group testGroup = new Group("someGroupname", new User("234345345"));
        testGroup.setId(dummyId);
        testGroup.addMember(sessionTestUser);

        List<Event> dummyEvents = Arrays.asList(
                new Meeting("someMeeting", Timestamp.from(Instant.now()), sessionTestUser, testGroup, "someLoc"),
                new Vote("someMeeting", Timestamp.from(Instant.now()), sessionTestUser, testGroup));
        List<LogBook> dummyLogbooks = Arrays.asList(new LogBook(sessionTestUser, testGroup, "Do stuff", Timestamp.valueOf(LocalDateTime.now().plusDays(2L))),
                                                  new LogBook(sessionTestUser, testGroup, "Do more stuff", Timestamp.valueOf(LocalDateTime.now().plusDays(5L))));
        List<GroupLog> dummyGroupLogs = Arrays.asList(new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_ADDED, 0L, "guy joined"),
                                                      new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_REMOVED, 0L, "other guy left"));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDateTime start = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime end = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(userManagementServiceMock.getUserById(sessionTestUser.getId())).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getGroupEventsInPeriod(testGroup, start, end)).thenReturn(dummyEvents);
        when(logBookServiceMock.getLogBookEntriesInPeriod(dummyId, start, end)).thenReturn(dummyLogbooks);
        when(groupLogServiceMock.getLogsForGroup(testGroup, start, end)).thenReturn(dummyGroupLogs);
        when(groupManagementServiceMock.getMonthsGroupActive(testGroup)).thenReturn(dummyMonths);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup, null)).thenReturn(true);

        mockMvc.perform(get("/group/history").param("groupId", String.valueOf(dummyId))).
                andExpect(view().name("group/history")).
                andExpect(model().attribute("group", hasProperty("id", is(dummyId)))).
                andExpect(model().attribute("eventsInPeriod", is(dummyEvents))).
                andExpect(model().attribute("logBooksInPeriod", is(dummyLogbooks))).
                andExpect(model().attribute("groupLogsInPeriod", is(dummyGroupLogs))).
                andExpect(model().attribute("monthsToView", is(dummyMonths)));

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(groupManagementServiceMock, times(1)).getMonthsGroupActive(testGroup);
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, testGroup, null);
        verifyNoMoreInteractions(groupManagementServiceMock);
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

        List<Event> dummyEvents = Collections.singletonList(new Meeting("someMeeting", Timestamp.from(Instant.now()), sessionTestUser, testGroup, "someLoc"));
        List<LogBook> dummyLogBooks = Collections.singletonList(new LogBook(sessionTestUser, testGroup, "do stuff", Timestamp.valueOf(LocalDateTime.now())));
        List<GroupLog> dummyGroupLogs = Collections.singletonList(new GroupLog(dummyId, sessionTestUser.getId(), GroupLogType.GROUP_MEMBER_ADDED, 0L));
        List<LocalDate> dummyMonths = Arrays.asList(LocalDate.now(), LocalDate.now().minusMonths(1L));

        LocalDate lastMonth = LocalDate.now().minusMonths(1L);
        String monthToView = lastMonth.format(DateTimeFormatter.ofPattern("M-yyyy"));

        LocalDateTime start = LocalDate.parse("01-" + monthToView, DateTimeFormatter.ofPattern("dd-M-yyyy")).atStartOfDay();
        LocalDateTime end = start.plusMonths(1L);

        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup, null)).thenReturn(true);
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

        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(1)).isGroupPermissionAvailable(sessionTestUser, testGroup, null);
        verify(groupManagementServiceMock, times(1)).getMonthsGroupActive(testGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);
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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(post("/group/add_bulk").param("groupUid", String.valueOf(testGroup.getUid())).param("list",testNumbers))
                .andExpect(status().isOk()).andExpect(view().name("group/add_bulk_error"));
        verify(groupBrokerMock,times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(sessionTestUser.getUid(), testGroup.getUid(), testMembers);
      //  verifyNoMoreInteractions(userManagementServiceMock);

    }

}
