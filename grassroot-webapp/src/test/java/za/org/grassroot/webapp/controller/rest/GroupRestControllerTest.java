package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Created by Siyanda Mzam on 2016/03/16.
 */

public class GroupRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    GroupRestController groupRestController;

    String path = "/api/group/";
    List<Group> groups = new ArrayList<>();
    Set<Group> groupSet = new HashSet<>();
    MembershipInfo membershipInfo = new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getUsername());
    Set<MembershipInfo> membersToAdd = Sets.newHashSet();
    Event event = meetingEvent;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(groupRestController).build();
    }

    @Test
    public void createGroupShouldWork() throws Exception {

        settingUpDummyData(testGroup, groups, membershipInfo, membersToAdd);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, testEventDescription, null)).thenReturn(testGroup);
        mockMvc.perform(post(path + "create/{phoneNumber}/{code}", testUserPhone, testUserCode).param("groupName", testGroupName).param("description", testEventDescription)).andExpect(status().isCreated());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupBrokerMock).create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, meetingEvent.getDescription(), null);
    }

    @Test
    public void getUserGroupsShouldWork() throws Exception {

        sessionTestUser.setId(2L);
        GroupLog groupLog = new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_ADDED, sessionTestUser.getId());
        testGroup.addMember(sessionTestUser, "ROLE_GROUP_ORGANIZER");
        groupSet.add(testGroup);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getMostRecentEvent(testGroup)).thenReturn(event);
        when(permissionBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(groupSet);
        when(groupBrokerMock.getMostRecentLog(testGroup)).thenReturn(groupLog);
        mockMvc.perform(get(path + "list/{phoneNumber}/{code}", testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventManagementServiceMock).getMostRecentEvent(testGroup);
        verify(permissionBrokerMock).getActiveGroups(sessionTestUser, null);
        verify(groupBrokerMock).getMostRecentLog(testGroup);
    }

    @Test
    public void searchForGroupsShouldWork() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.findGroupFromJoinCode(testSearchTerm)).thenReturn(testGroup);
        mockMvc.perform(get(path + "search").param("searchTerm", testSearchTerm)).andExpect(status().is2xxSuccessful());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock).findGroupFromJoinCode(testSearchTerm);
    }

    @Test
    public void searchRequestToJoinGroup() throws Exception {

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupJoinRequestServiceMock.open(sessionTestUser.getUid(), testGroup.getUid(), null)).thenReturn(testGroup.getUid());
        mockMvc.perform(post(path + "join/request/{phoneNumber}/{code}", testUserPhone, testUserCode).param("uid", testGroup.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupJoinRequestServiceMock).open(sessionTestUser.getUid(), testGroup.getUid(), null);
    }

    @Test
    public void gettingAGroupMemberShouldWork() throws Exception {

        List<User> userList = new ArrayList<>();
        Page<User> userPage = new PageImpl<>(userList, new PageRequest(0, 5), 1);

        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        when(permissionBrokerMock.isGroupPermissionAvailable(sessionTestUser, testGroup,
                                                             Permission.GROUP_PERMISSION_SEE_MEMBER_DETAILS)).thenReturn(true);

        when(userManagementServiceMock.getGroupMembers(testGroup, 0, 5)).thenReturn(userPage);
        mockMvc.perform(get(path + "/members/list/{phoneNumber}/{code}/{groupUid}/", testUserPhone, testUserCode, testGroup.getUid())
                                .param("page", String.valueOf(0))
                                .param("size", String.valueOf(5)))
                .andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupBrokerMock).load(testGroup.getUid());
        verify(userManagementServiceMock).getGroupMembers(testGroup, 0, 5);
    }
    private void settingUpDummyData(Group group, List<Group> groups, MembershipInfo membershipInfo, Set<MembershipInfo> membersToAdd) {

        membersToAdd.add(membershipInfo);
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER);
        groups.add(group);
        group.setDescription(testEventDescription);
    }
}
