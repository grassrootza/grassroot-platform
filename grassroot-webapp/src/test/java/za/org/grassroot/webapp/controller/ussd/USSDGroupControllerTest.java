package za.org.grassroot.webapp.controller.ussd;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Membership;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.UIDGenerator;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.*;

/**
 * Created by luke on 2015/11/28.
 */
public class USSDGroupControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDGroupControllerTest.class);

    private static final String testUserPhone = "27801110000";
    private static final String testUserUid = "test-user-unique-id";
    private static final String phoneParam = "msisdn";
    private static final String groupParam = "groupId";
    private static final String testGroupIdString = "1";

    private static final String path = "/ussd/group/";

    private User testUser;
    private Group testGroup;
    private Set<MembershipInfo> testMembers = new HashSet<>();
    private GroupPermissionTemplate template = GroupPermissionTemplate.DEFAULT_GROUP;

    @InjectMocks
    USSDGroupController ussdGroupController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdGroupController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdGroupController, ussdGroupUtil);

        testUser = new User(testUserPhone);
        testGroup = new Group("test group", testUser);
        testMembers.add(new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, null));

    }

    @Test
    public void openingMenuShouldWorkWithNoGroups() throws Exception {
        resetTestGroup();
        testGroup.addMember(testUser);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.hasActiveGroupsPartOf(testUser)).thenReturn(false);
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).hasActiveGroupsPartOf(testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void existingGroupMenuShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("menu", testGroup.getId());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupBrokerMock.isDeactivationAvailable(testUser, testGroup)).thenReturn(false);

        mockMvc.perform(get(path + "menu").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(2)).isDeactivationAvailable(testUser, testGroup);
        verify(groupManagementServiceMock, times(2)).loadGroup(testGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void renameExistingGroupMenuShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithParams("rename", testGroup.getId(), "");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        mockMvc.perform(get(path + "rename").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).loadGroup(testGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void renameConfirmShouldWork() throws Exception {
        // todo: test prior input & new group ranges

        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.renameGroup(testGroup.getUid(), "a renamed test group", testUserUid)).thenReturn(testGroup);
        mockMvc.perform(get(path + "rename-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid()).
                param("request", "a renamed test group")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).renameGroup(testGroup.getUid(), "a renamed test group", testUser.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void groupNewTokenPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("token", testGroup.getId());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.groupHasValidToken(testGroup)).thenReturn(false);

        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());


        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(2)).groupHasValidToken(testGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void setTokenExpiryShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.generateExpiringGroupToken(testGroup.getUid(), testUser.getUid(), 3)).thenReturn(testGroup);

        mockMvc.perform(get(path + "token-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("days", "3")).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).generateExpiringGroupToken(testGroup.getUid(), testUser.getUid(), 3);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void groupExistingTokenMenuShouldWork() throws Exception{
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        testGroup.setTokenExpiryDateTime(Timestamp.valueOf(LocalDateTime.now().plusDays(3)));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup.getId()))).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.groupHasValidToken(testGroup)).thenReturn(true);
        mockMvc.perform(get(path + "token").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token", testGroup.getId()));
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).groupHasValidToken(testGroup);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void extendTokenShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setTokenExpiryDateTime(Timestamp.valueOf(LocalDateTime.now()));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getId()))).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.extendGroupToken(testGroup, 3, testUser)).thenReturn(testGroup);
        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        testGroup.setTokenExpiryDateTime(new Timestamp(DateTimeUtil.addHoursToDate(new Date(), 72).getTime()));
        mockMvc.perform(get(path + "token-extend").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("days", "3")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, saveGroupMenu("token-extend", testGroup.getId()));
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).extendGroupToken(testGroup, 3, testUser);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void closeTokenShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setGroupTokenCode("123");
        String urlToSave = saveGroupMenu("token-close", testGroup.getId());

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);

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
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).closeGroupToken(testGroup.getUid(), testUser.getUid());

        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void addNumberPromptShouldWork() throws Exception {
        // todo: once permissions implemented, add tests for error throwing if user doesn't have permission
        resetTestGroup();
        String urlToSave = saveGroupMenu("addnumber", testGroup.getId());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path + "addnumber").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void addNumberConfirmShouldWork() throws Exception {
        // todo: as for previous test, once permissions added, test that errrors are thrown (likewise, for bad input)
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroupByUid(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "addnumber-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", "0801110001")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), ordinaryMember("0801110001"));
        verifyNoMoreInteractions(groupBrokerMock);
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).loadGroupByUid(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void unsubscribePromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("unsubscribe", testGroup.getId());
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        mockMvc.perform(get(path + "unsubscribe").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        // note: not verifying zero group interactions as may add them in future
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void unsubscribeConfirmShouldWork() throws Exception {
        resetTestGroup();
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        mockMvc.perform(get(path + "unsubscribe-do").param(phoneParam, testUserPhone).param(groupParam, testGroup.getUid())).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).removeMembers(testUser.getUid(), testGroup.getUid(), Sets.newHashSet(testUser.getUid()));
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void consolidateMenuShoudlWorkIfNoCandidates() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("merge", testGroup.getId());
        List<Group> emptyList = new ArrayList<>();

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.getMergeCandidates(testUser, testGroup.getId())).thenReturn(emptyList);

        mockMvc.perform(get(path + "merge").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).getMergeCandidates(testUser, testGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void consolidateMenuShouldWorkWithCandidates() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenu("merge", testGroup.getId());
        Group unnamedTestGroup = new Group("", testUser);
        unnamedTestGroup.setCreatedDateTime(Timestamp.valueOf(LocalDateTime.now()));
        List<Group> testList = Arrays.asList(unnamedTestGroup, new Group("tg1", testUser), new Group("tg2", testUser));
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.getMergeCandidates(testUser, testGroup.getId())).thenReturn(testList);

        mockMvc.perform(get(path + "merge").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(base + urlToSave).param(phoneParam, testUserPhone).param(userChoiceParam, interruptedChoice)).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).getMergeCandidates(testUser, testGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
    }

    @Test
    public void confirmConsolidatePromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithParams("merge-confirm", 2L, "&firstGroupSelected=" + testGroup.getId());
        Group mergingGroup = new Group("tg1", testUser);
        mergingGroup.setId(2L);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(1L)).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroup(2L)).thenReturn(mergingGroup);
        mockMvc.perform(get(path + "merge-confirm").param(phoneParam, testUserPhone).param(groupParam, "2").
                param("firstGroupSelected", "" + testGroup.getId())).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(2)).loadGroup(anyLong());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void consolidateGroupDoneScreenShouldWork() throws Exception {
        // todo: also test the exception catch & error menu
        resetTestGroup();
        Group mergingGroup = new Group("tg1", testUser);
        mergingGroup.setId(2L);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroup(mergingGroup.getId())).thenReturn(mergingGroup);
        when(groupBrokerMock.merge(testUser.getUid(), testGroup.getUid(), mergingGroup.getUid(),
                                                    false, false, false, null)).thenReturn(mergingGroup);
        mockMvc.perform(get(path + "merge-do").param(phoneParam, testUserPhone).param("groupId1", testGroupIdString).
                param("groupId2", "" + mergingGroup.getId()).param("action", "inactive")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).merge(testUser.getUid(), testGroup.getUid(), mergingGroup.getUid(), false, false, false, null);
        verifyNoMoreInteractions(groupBrokerMock);
        verify(groupManagementServiceMock, times(2)).loadGroup(anyLong());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void inactiveConfirmShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setActive(true);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, null)).thenReturn(testUser);
        mockMvc.perform(get(path + "inactive").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void setInactiveDoneShouldWork() throws Exception {
        resetTestGroup();
        testGroup.setActive(true);
        Group errorGroup = new Group("error", testUser);
        errorGroup.setId(2L);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroup(errorGroup.getId())).thenReturn(errorGroup);
        when(groupBrokerMock.isDeactivationAvailable(testUser, testGroup)).thenReturn(true);
        when(groupBrokerMock.isDeactivationAvailable(testUser, errorGroup)).thenReturn(false);

        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString)).
                andExpect(status().isOk());
        mockMvc.perform(get(path + "inactive-do").param(phoneParam, testUserPhone).param(groupParam, "" + errorGroup.getId())).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(2)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).isDeactivationAvailable(testUser, testGroup);
        verify(groupBrokerMock, times(1)).isDeactivationAvailable(testUser, errorGroup);
        verify(groupBrokerMock, times(1)).deactivate(testUser.getUid(), testGroup.getUid());
        verifyNoMoreInteractions(groupBrokerMock);
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).loadGroup(errorGroup.getId());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void newGroupPromptShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = "group/create";
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + "create").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void newGroupCreateShouldWork() throws Exception {
        resetTestGroup();
        String nameToPass = "test group";
        String urlToSave = saveGroupMenuWithInput("create-do", testGroup.getId(), nameToPass);
        String testGroupUid = "unique-group-id";
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupBrokerMock.create(testUser.getUid(), nameToPass, null, organizer(testUser), template)).thenReturn(testGroup);

        mockMvc.perform(get(path + "create-do").param(phoneParam, testUserPhone).param("request", nameToPass)).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).setLastUssdMenu(testUser, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock, times(1)).create(testUser.getUid(), nameToPass, null, testMembers, template);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void newGroupBatchNumbersShouldWork() throws Exception {

        resetTestGroup();
        String newNumbersToPass = "0801234567 010111222"; // second number is invalid
        Set<MembershipInfo> member = ordinaryMember("0801234567");
        String urlToSave = saveGroupMenuWithInput("add-numbers-do", testGroup.getId(), newNumbersToPass);

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupManagementServiceMock.loadGroup(testGroup.getId())).thenReturn(testGroup);
        when(groupManagementServiceMock.loadGroupByUid(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path + "add-numbers-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", newNumbersToPass)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock, times(1)).loadGroup(testGroup.getId());
        verify(groupManagementServiceMock, times(1)).loadGroupByUid(testGroup.getUid());
        verifyNoMoreInteractions(groupManagementServiceMock);
        verify(groupBrokerMock, times(1)).addMembers(testUser.getUid(), testGroup.getUid(), member);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    @Test
    public void newGroupFinishingShouldWork() throws Exception {
        resetTestGroup();
        String urlToSave = saveGroupMenuWithInput("add-numbers-do", testGroup.getId(), "0");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + "add-numbers-do").param(phoneParam, testUserPhone).param(groupParam, testGroupIdString).
                param("request", "0")).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupManagementServiceMock);
        verifyZeroInteractions(eventManagementServiceMock);
    }

    /*
    Helper method to reset testGroup to pristine state
     */
    private void resetTestGroup() {
        testGroup.setGroupName("test group");
        testGroup.setId(1L);
        testGroup.addMember(testUser);
    }

}
