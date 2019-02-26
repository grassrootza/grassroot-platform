package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import za.org.grassroot.core.domain.Permission;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.domain.group.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.ussd.group.UssdGroupMgmtService;
import za.org.grassroot.webapp.controller.ussd.group.UssdGroupMgmtServiceImpl;
import za.org.grassroot.webapp.controller.ussd.group.UssdGroupService;
import za.org.grassroot.webapp.controller.ussd.group.UssdGroupServiceImpl;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/11/28.
 */
public class UssdGroupServiceTest extends UssdUnitTest {

    private static final String testUserPhone = "27801110000";
    private static final String testUserUid = "test-user-unique-id";
    private static final String phoneParam = "msisdn";
    private static final String groupParam = "groupUid";
    private String testGroupIdString;

    private User testUser;
    private Group testGroup;
    private Set<MembershipInfo> testMembers = new HashSet<>();
    private GroupPermissionTemplate template = GroupPermissionTemplate.DEFAULT_GROUP;

    private UssdGroupService ussdGroupService;
    private UssdGroupMgmtService ussdGroupMgmtService;

    @Before
    public void setUp() {
        testUser = new User(testUserPhone, null, null);
        testGroup = new Group("test group", GroupPermissionTemplate.DEFAULT_GROUP, testUser);
        testMembers.add(new MembershipInfo(testUserPhone, GroupRole.ROLE_GROUP_ORGANIZER, null));
        testGroupIdString = testGroup.getUid();

        this.ussdGroupService = new UssdGroupServiceImpl(false, ussdSupport, groupBrokerMock, permissionBrokerMock, null, groupJoinRequestServiceMock, ussdGroupUtil, userManagementServiceMock, cacheUtilManagerMock);
        this.ussdGroupMgmtService = new UssdGroupMgmtServiceImpl(groupBrokerMock, groupQueryBrokerMock, permissionBrokerMock, ussdSupport, userManagementServiceMock);
    }

    @Test
    public void groupSecondPageShouldWork() throws Exception {
        resetTestGroup();
        List<Group> testGroups = Arrays.asList(new Group("gc1", GroupPermissionTemplate.DEFAULT_GROUP, testUser),
                new Group("gc2", GroupPermissionTemplate.DEFAULT_GROUP, testUser),
                new Group("gc3", GroupPermissionTemplate.DEFAULT_GROUP, testUser),
                new Group("gc4", GroupPermissionTemplate.DEFAULT_GROUP, testUser));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, null)).thenReturn(4);
        when(permissionBrokerMock.getPageOfGroups(testUser, null, 1, 3)).thenReturn(testGroups);

        this.ussdGroupService.processGroupPaginationHelper(testUserPhone, "Look at pages", 1, "/ussd/blank", null, "/ussd/blank2");

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, null);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, null, 1, 3);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

    @Test
    public void openingMenuShouldWorkWithNoGroups() throws Exception {
        resetTestGroup();
        testGroup.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdGroupService.processGroupList(testUserPhone, false);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void createGroupWithInvalidNameShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        this.ussdGroupService.processCreateGroupWithName(testUserPhone, "1", false, null);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, "group/create");
        verifyZeroInteractions(groupBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void existingGroupMenuShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("menu", testGroup.getUid());

        Permission p1 = Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER;
        Permission p2 = Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS;

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(testUser, testGroup, p1)).thenReturn(true);
        when(permissionBrokerMock.isGroupPermissionAvailable(testUser, testGroup, p2)).thenReturn(true);

        this.ussdGroupService.processGroupMenu(testUserPhone, testGroupIdString);
        this.ussdGroupService.processGroupMenu(testUserPhone, testGroup.getUid());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(2)).isGroupPermissionAvailable(testUser, testGroup, p1);
        verify(permissionBrokerMock, times(2)).isGroupPermissionAvailable(testUser, testGroup, p2);
    }

    @Test
    public void renameExistingGroupMenuShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithParams("rename", testGroup.getUid(), "");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        this.ussdGroupMgmtService.processRenamePrompt(testUserPhone, testGroupIdString);
        this.ussdGroupMgmtService.processRenamePrompt(testUserPhone, testGroup.getUid());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

//    @Test
//    public void renameConfirmShouldWork() throws Exception {
//        // todo: test prior input & new testGroup ranges
//
//        resetTestGroup();
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        mockMvc.perform(get(path + "rename-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid()).
//                param("request", "a renamed test testGroup")).andExpect(status().isOk());
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).updateName(testUser.getUid(), testGroup.getUid(), "a renamed test testGroup");
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }

//    @Test
//    public void groupNewTokenPromptShouldWork() throws Exception {
//        resetTestGroup();
//        String urlToSave = saveGroupMenu("token", testGroup.getUid());
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//
//        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
//                andExpect(status().isOk());
//
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void setTokenExpiryShouldWork() throws Exception {
//
//        // note: the handling of the timestamps is going to be a bit tricky
//        resetTestGroup();
//        testGroup.setGroupTokenCode("123");
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        when(groupBrokerMock.openJoinToken(testUserUid, testGroup.getUid(), LocalDateTime.now().plusDays(3))).
//                thenReturn("abc");
//
//        mockMvc.perform(get(path + "token-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid()).
//                param("days", "3")).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verify(groupBrokerMock, times(1)).openJoinToken(eq(testUser.getUid()), eq(testGroup.getUid()), any(LocalDateTime.class));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void groupExistingTokenMenuShouldWork() throws Exception{
//        resetTestGroup();
//        testGroup.setGroupTokenCode("123");
//        testGroup.setTokenExpiryDateTime(Instant.now().plus(3, ChronoUnit.DAYS));
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup.getUid()))).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
//                andExpect(status().isOk());
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup. getUid()));
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void extendTokenShouldWork() throws Exception {
//        resetTestGroup();
//        testGroup.setTokenExpiryDateTime(Instant.now());
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getUid()))).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
//                andExpect(status().isOk());
//        testGroup.setTokenExpiryDateTime(Instant.now().plus(72, ChronoUnit.HOURS));
//        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
//                param("days", "3")).andExpect(status().isOk());
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getUid()));
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
//        verify(groupBrokerMock, times(1)).openJoinToken(eq(testUser.getUid()), eq(testGroup.getUid()), any());
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void closeTokenShouldWork() throws Exception {
//        resetTestGroup();
//        testGroup.setGroupTokenCode("123");
//        String urlToSave = saveGroupMenu("token-close", testGroup.getUid());
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//
//        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
//                andExpect(status().isOk());
//        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
//                andExpect(status().isOk());
//        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
//                param("confirmed", "yes")).andExpect(status().isOk());
//        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
//                param("confirmed", "no")).andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).closeJoinToken(testUser.getUid(), testGroup.getUid());
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }

    @Test
    public void addNumberPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("addnumber", testGroup.getUid());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        this.ussdGroupService.processAddNumberInput(testUserPhone, testGroupIdString);
        this.ussdGroupService.processAddNumberInput(testUserPhone, testGroup.getUid());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void addNumberConfirmShouldWork() throws Exception {
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        this.ussdGroupService.processAddNumberToGroup(testUserPhone, testGroupIdString, "0801110001");

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801110001"),
                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void unsubscribePromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("unsubscribe", testGroup.getUid());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        this.ussdGroupService.processUnsubscribeConfirm(testUserPhone, testGroupIdString);
        this.ussdGroupService.processUnsubscribeConfirm(testUserPhone, testGroup.getUid());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        // note: not verifying zero testGroup interactions as may add them in future
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void unsubscribeConfirmShouldWork() throws Exception {
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);

        this.ussdGroupService.processUnsubscribeDo(testUserPhone, testGroup.getUid());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).unsubscribeMember(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

//    @Test
//    public void inactiveConfirmShouldWork() throws Exception {
//        resetTestGroup();
//        testGroup.setActive(true);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
//        mockMvc.perform(get(path + "inactive").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
//                andExpect(status().isOk());
//        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verifyNoMoreInteractions(groupBrokerMock);
//        verifyZeroInteractions(eventBrokerMock);
//    }
//
//    @Test
//    public void setInactiveDoneShouldWork() throws Exception {
//        resetTestGroup();
//        testGroup.setActive(true);
//        Group errorGroup = new Group("error", testUser);
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
//        when(groupBrokerMock.load(errorGroup.getUid())).thenReturn(errorGroup); // test exception throwing later
//
//        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
//                andExpect(status().isOk());
//        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, errorGroup.getUid())).
//                andExpect(status().isOk());
//
//        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
//        verifyNoMoreInteractions(userManagementServiceMock);
//        verify(groupBrokerMock, times(1)).deactivate(testUser.getUid(), testGroup.getUid(), true);
//        verify(groupBrokerMock, times(1)).deactivate(testUser.getUid(), errorGroup.getUid(), true);
//        // verifyNoMoreInteractions(groupBrokerMock); // need to test exception is thrown ...
//        verifyZeroInteractions(eventBrokerMock);
//    }

    @Test
    public void newGroupPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = "group/create";
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        this.ussdGroupService.processCreatePrompt(testUserPhone);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void newGroupCreateShouldWork() throws Exception {
        resetTestGroup();
        String nameToPass = "test testGroup";
        String urlToSave = saveGroupMenuWithInput("create-do", testGroup.getUid(), nameToPass, false);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupBrokerMock.create(testUser.getUid(), nameToPass, null, organizer(testUser), template,
                null, null, true, false, true)).thenReturn(testGroup);

        this.ussdGroupService.processCreateGroupWithName(testUserPhone, nameToPass, false, null);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).create(testUser.getUid(), nameToPass, null, testMembers, template,
                null, null, true, false, true);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void newGroupBatchNumbersShouldWork() throws Exception {
        resetTestGroup();
        String newNumbersToPass = "0801234567 010111222"; // second number is invalid

        Set<MembershipInfo> member = ordinaryMember("0801234567");

        String urlToSave = saveGroupMenuWithInput("add-numbers-do", testGroup.getUid(), newNumbersToPass, false);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        this.ussdGroupService.processAddNumbersToNewlyCreatedGroup(testUserPhone, testGroupIdString, newNumbersToPass, null);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), member,
                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    // helper method to generate a set of membership info ... used often
    protected Set<MembershipInfo> ordinaryMember(String phoneNumber) {
        return Sets.newHashSet(new MembershipInfo(phoneNumber, GroupRole.ROLE_ORDINARY_MEMBER, null));
    }

    protected Set<MembershipInfo> organizer(User user) {
        return Sets.newHashSet(new MembershipInfo(user.getPhoneNumber(), GroupRole.ROLE_GROUP_ORGANIZER, user.getDisplayName()));
    }

    @Test
    public void newGroupFinishingShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithInput("add-numbers-do", testGroup.getUid(), "0", false);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        this.ussdGroupService.processAddNumbersToNewlyCreatedGroup(testUserPhone, testGroupIdString, "0", null);

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

//    @Test
//    public void sendAllGroupJoinCodesNotificationShouldWork()throws Exception{
//        resetTestGroup();
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//
//        List<Group> groups = new ArrayList<>();
//        groups.add(testGroup);
//
//        when(groupRepositoryMock.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(testUser)).thenReturn(groups);
//        String testMessage = "Test message";
//        List<String> testMessages = new ArrayList<>();
//        testMessages.add(testMessage);
//        when(messageAssemblingServiceMock.getMessagesForGroups(groups)).thenReturn(testMessages);
//
//        Notification notification = new JoinCodeNotification(testUser,"Your groups codes",
//                new UserLog(testUser.getUid(), UserLogType.SENT_GROUP_JOIN_CODE,"All groups join codes", UserInterfaceType.UNKNOWN));
//
//        mockMvc.perform(get(path + "sendall")
//                .param(phoneParam,""+testUserPhone)
//                .param("notification",""+notification))
//                .andExpect(status().is(200));
//        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
//    }
//
//    @Test
//    public void sendCreatedGroupJoinCodeShouldWork() throws Exception{
//        resetTestGroup();
//        testUser = new User(testUserPhone,"Test User", null);
//        String testMessage = "Group join code";
//        when(messageAssemblingServiceMock.createGroupJoinCodeMessage(testGroup)).thenReturn(testMessage);
//
//        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
//
//        mockMvc.perform(get(path + "send-code")
//                .param(phoneParam,""+testUserPhone)
//                .param(groupParam,""+testGroup.getUid())
//                .param("message",""+testMessage))
//                .andExpect(status().is(200));
//        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
//        verify(groupBrokerMock,times(1)).sendGroupJoinCodeNotification(testUser.getUid(),testGroup.getUid());
//    }

    /*
    Helper method to reset testGroup to pristine state
     */
    private void resetTestGroup() {
        testGroup.setGroupName("test testGroup");
        testGroup.addMember(testUser, GroupRole.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
    }

}
