package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.JoinCodeNotification;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.group.GroupJoinRequestService;
import za.org.grassroot.services.group.GroupPermissionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/11/28.
 */
public class USSDGroupControllerTest extends USSDAbstractUnitTest {

    // private static final Logger log = LoggerFactory.getLogger(USSDGroupControllerTest.class);

    private static final String testUserPhone = "27801110000";
    private static final String testUserUid = "test-user-unique-id";
    private static final String phoneParam = "msisdn";
    private static final String groupParam = "groupUid";
    private String testGroupIdString;

    private static final String path = "/ussd/group/";

    private User testUser;
    private Group testGroup;
    private Set<MembershipInfo> testMembers = new HashSet<>();
    private GroupPermissionTemplate template = GroupPermissionTemplate.DEFAULT_GROUP;

    @Mock private GroupJoinRequestService groupJoinRequestServiceMock;
    @Mock private MessageAssemblingService messageAssemblingServiceMock;

    @InjectMocks
    private USSDGroupController ussdGroupController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdGroupController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdGroupController);
        ussdGroupController.setUssdGroupUtil(ussdGroupUtil);

        testUser = new User(testUserPhone, null, null);
        testGroup = new Group("test group", testUser);
        testMembers.add(new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, null));
        testGroupIdString = testGroup.getUid();
    }

    @Test
    public void groupSecondPageShouldWork() throws Exception {
        resetTestGroup();
        List<Group> testGroups = Arrays.asList(new Group("gc1", testUser),
                new Group("gc2", testUser),
                new Group("gc3", testUser),
                new Group("gc4", testUser));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.countActiveGroupsWithPermission(testUser, null)).thenReturn(4);
        when(permissionBrokerMock.getPageOfGroups(testUser, null, 1, 3)).thenReturn(testGroups);

        mockMvc.perform(get("/ussd/group_page").param(phoneParam, testUserPhone).param("prompt", "Look at pages").
                param("page", "1").param("existingUri", "/ussd/blank").param("newUri", "/ussd/blank2")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, null);
        verify(permissionBrokerMock, times(1)).getPageOfGroups(testUser, null, 1, 3);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(permissionBrokerMock);
    }

    @Test
    public void openingMenuShouldWorkWithNoGroups() throws Exception {
        resetTestGroup();
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(permissionBrokerMock.getActiveGroupsWithPermission(testUser, null)).thenReturn(new HashSet<>());
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(permissionBrokerMock, times(1)).countActiveGroupsWithPermission(testUser, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void createGroupWithInvalidNameShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get(path + "create-do").param(phoneParam, testUserPhone).param(userInputParam, "1")).
                andExpect(status().isOk());
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

        mockMvc.perform(get(path + "menu").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verify(permissionBrokerMock, times(2)).isGroupPermissionAvailable(testUser, testGroup, p1);
        verify(permissionBrokerMock, times(4)).isGroupPermissionAvailable(testUser, testGroup, p2);
    }

    @Test
    public void renameExistingGroupMenuShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithParams("rename", testGroup.getUid(), "");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "rename").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void renameConfirmShouldWork() throws Exception {
        // todo: test prior input & new testGroup ranges

        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        mockMvc.perform(get(path + "rename-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid()).
                param("request", "a renamed test testGroup")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).updateName(testUser.getUid(), testGroup.getUid(), "a renamed test testGroup");
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void groupNewTokenPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("token", testGroup.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());


        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void setTokenExpiryShouldWork() throws Exception {

        // note: the handling of the timestamps is going to be a bit tricky
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.openJoinToken(testUserUid, testGroup.getUid(), LocalDateTime.now().plusDays(3))).
                thenReturn("abc");

        mockMvc.perform(get(path + "token-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid()).
                param("days", "3")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(groupBrokerMock, times(1)).openJoinToken(eq(testUser.getUid()), eq(testGroup.getUid()), any(LocalDateTime.class));
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void groupExistingTokenMenuShouldWork() throws Exception{
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        testGroup.setTokenExpiryDateTime(Instant.now().plus(3, ChronoUnit.DAYS));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup.getUid()))).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup. getUid()));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void extendTokenShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setTokenExpiryDateTime(Instant.now());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getUid()))).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        testGroup.setTokenExpiryDateTime(Instant.now().plus(72, ChronoUnit.HOURS));
        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("days", "3")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getUid()));
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(testGroup.getUid());
        verify(groupBrokerMock, times(1)).openJoinToken(eq(testUser.getUid()), eq(testGroup.getUid()), any());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void closeTokenShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        String urlToSave = saveGroupMenu("token-close", testGroup.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());
        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("confirmed", "yes")).andExpect(status().isOk());
        mockMvc.perform(get(path + "token-close").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("confirmed", "no")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).closeJoinToken(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void addNumberPromptShouldWork() throws Exception {
        // todo: once permissions implemented, add tests for error throwing if user doesn't have permission
        resetTestGroup();
        String urlToSave = saveGroupMenu("addnumber", testGroup.getUid());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path + "addnumber").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void addNumberConfirmShouldWork() throws Exception {
        // todo: as for previous test, once permissions added, test that errrors are thrown (likewise, for bad input)
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "addnumber-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", "0801110001")).andExpect(status().isOk());
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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "unsubscribe").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        // note: not verifying zero testGroup interactions as may add them in future
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void unsubscribeConfirmShouldWork() throws Exception {
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        mockMvc.perform(get(path + "unsubscribe-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).unsubscribeMember(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void consolidateMenuShoudlWorkIfNoCandidates() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("merge", testGroup.getUid());
        Set<Group> emptyList = new HashSet<>();

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupQueryBrokerMock.mergeCandidates(testUser.getUid(), testGroup.getUid())).thenReturn(emptyList);

        mockMvc.perform(get(path + "merge").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupQueryBrokerMock, times(2)).mergeCandidates(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupQueryBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void consolidateMenuShouldWorkWithCandidates() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("merge", testGroup.getUid());
        Group unnamedTestGroup = new Group("", testUser);
        Set<Group> testList = new HashSet<>(Arrays.asList(unnamedTestGroup, new Group("tg1", testUser), new Group("tg2", testUser)));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupQueryBrokerMock.mergeCandidates(testUser.getUid(), testGroup.getUid())).thenReturn(testList);

        mockMvc.perform(get(path + "merge").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupQueryBrokerMock, times(2)).mergeCandidates(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupQueryBrokerMock);
    }

    @Test
    public void confirmConsolidatePromptShouldWork() throws Exception {
        resetTestGroup();
        Group mergingGroup = new Group("tg1", testUser);
        String urlToSave = saveGroupMenuWithParams("merge-confirm",
                                                   mergingGroup.getUid(), "&firstGroupSelected=" + testGroup.getUid());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(mergingGroup.getUid())).thenReturn(mergingGroup);

        mockMvc.perform(get(path + "merge-confirm").param(phoneParam, testUserPhone).param(groupParam, mergingGroup.getUid()).
                param("firstGroupSelected", testGroup.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).load(anyString());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void consolidateGroupDoneScreenShouldWork() throws Exception {
        resetTestGroup();
        Group mergingGroup = new Group("tg1", testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(mergingGroup.getUid())).thenReturn(mergingGroup);
        when(groupBrokerMock.merge(testUser.getUid(), testGroup.getUid(), mergingGroup.getUid(),
                                                    false, false, false, null)).thenReturn(mergingGroup);
        mockMvc.perform(get(path + "merge-do").param(phoneParam, testUserPhone).param("groupUid1", testGroup.getUid()).
                param("groupUid2", "" + mergingGroup.getUid()).param("action", "inactive")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).merge(testUser.getUid(), testGroup.getUid(), mergingGroup.getUid(), false, false, false, null);
        verify(groupBrokerMock, times(2)).load(anyString());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void inactiveConfirmShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setActive(true);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        mockMvc.perform(get(path + "inactive").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void setInactiveDoneShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setActive(true);
        Group errorGroup = new Group("error", testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(errorGroup.getUid())).thenReturn(errorGroup); // test exception throwing later

        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, errorGroup.getUid())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).deactivate(testUser.getUid(), testGroup.getUid(), true);
        verify(groupBrokerMock, times(1)).deactivate(testUser.getUid(), errorGroup.getUid(), true);
        // verifyNoMoreInteractions(groupBrokerMock); // need to test exception is thrown ...
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void newGroupPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = "group/create";
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + "create").param(phoneParam, testUserPhone)).andExpect(status().isOk());
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
        when(groupBrokerMock.create(testUser.getUid(), nameToPass, null, organizer(testUser), template, null, null, true, false)).thenReturn(testGroup);

        mockMvc.perform(get(path + "create-do").param(phoneParam, testUserPhone).param("request", nameToPass)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(cacheUtilManagerMock, times(1)).putUssdMenuForUser(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).create(testUser.getUid(), nameToPass, null, testMembers, template, null, null, true, false);
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
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "add-numbers-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", newNumbersToPass)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), member,
                GroupJoinMethod.ADDED_BY_OTHER_MEMBER, false);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void newGroupFinishingShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithInput("add-numbers-do", testGroup.getUid(), "0", false);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "add-numbers-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", "0")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).load(testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void sendAllGroupJoinCodesNotificationShouldWork()throws Exception{
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        List<Group> groups = new ArrayList<>();
        groups.add(testGroup);

        when(groupRepositoryMock.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(testUser)).thenReturn(groups);
        String testMessage = "Test message";
        List<String> testMessages = new ArrayList<>();
        testMessages.add(testMessage);
        when(messageAssemblingServiceMock.getMessagesForGroups(groups)).thenReturn(testMessages);

        Notification notification = new JoinCodeNotification(testUser,"Your groups codes",
                new UserLog(testUser.getUid(), UserLogType.SENT_GROUP_JOIN_CODE,"All groups join codes", UserInterfaceType.UNKNOWN));

        mockMvc.perform(get(path + "sendall")
                .param(phoneParam,""+testUserPhone)
                .param("notification",""+notification))
                .andExpect(status().is(200));
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
    }

    @Test
    public void sendCreatedGroupJoinCodeShouldWork() throws Exception{
        resetTestGroup();
        testUser = new User(testUserPhone,"Test User", null);
        String testMessage = "Group join code";
        when(messageAssemblingServiceMock.createGroupJoinCodeMessage(testGroup)).thenReturn(testMessage);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        mockMvc.perform(get(path + "send-code")
                .param(phoneParam,""+testUserPhone)
                .param(groupParam,""+testGroup.getUid())
                .param("message",""+testMessage))
                .andExpect(status().is(200));
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verify(groupBrokerMock,times(1)).sendGroupJoinCodeNotification(testUser.getUid(),testGroup.getUid());
    }

    /*
    Helper method to reset testGroup to pristine state
     */
    private void resetTestGroup() {
        testGroup.setGroupName("test testGroup");
        testGroup.addMember(testUser, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
    }

}
