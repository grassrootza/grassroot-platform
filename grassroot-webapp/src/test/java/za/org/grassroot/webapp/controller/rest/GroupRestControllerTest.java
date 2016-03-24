package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Event;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.Meeting;
import za.org.grassroot.services.MembershipInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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
    Group group = new Group(testGroupName, sessionTestUser);
    List<Group> groups = new ArrayList<>();
    MembershipInfo membershipInfo = new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getUsername());
    Set<MembershipInfo> membersToAdd = Sets.newHashSet();
    Event event = new Meeting("The Test Meeting", testTimestamp, sessionTestUser, group, "At the Jozi-Hub");

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(groupRestController).build();
    }
    @Test
    public void createGroupShouldWork() throws Exception {

        settingUpDummyData(group, groups, membershipInfo, membersToAdd);
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.changeGroupDescription(group.getUid(), "This is a test group", sessionTestUser.getUid())).thenReturn(group);
        mockMvc.perform(post(path + "create/{phoneNumber}/{code}", testUserPhone, testUserCode).param("groupName", testGroupName).param("description", "This is a required group")).andExpect(status().isCreated());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupManagementServiceMock).changeGroupDescription(group.getUid(), "This is a test a group", sessionTestUser.getUid());
    }
    @Test
    public void getUserGroupsShouldWork() throws Exception {

        settingUpDummyData(group, groups, membershipInfo, membersToAdd);
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(eventManagementServiceMock.getMostRecentEvent(group)).thenReturn(event);
        when(groupManagementServiceMock.getActiveGroupsPartOf(sessionTestUser)).thenReturn(groups);
        mockMvc.perform(get(path + "list/{phoneNumber}/{code}", testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(eventManagementServiceMock).getMostRecentEvent(group);
        verify(groupManagementServiceMock).getActiveGroupsPartOf(sessionTestUser);
    }
    @Test
    public void searchForGroupsShouldWork() throws Exception {
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupManagementServiceMock.findGroupByToken(testSearchTerm)).thenReturn(group);
        mockMvc.perform(get(path + "search").param("searchTerm", testSearchTerm)).andExpect(status().is2xxSuccessful());
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupManagementServiceMock).findGroupByToken(testSearchTerm);
    }
    @Test
    public void searchRequestToJoinGroup() throws Exception {
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(groupJoinRequestServiceMock.open(sessionTestUser.getUid(), group.getUid())).thenReturn(group.getUid());
        mockMvc.perform(post(path + "join/request/{phoneNumber}/{code}", testUserPhone, testUserCode).param("uid", group.getUid())).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(groupJoinRequestServiceMock).open(sessionTestUser.getUid(), group.getUid());
    }

    private void settingUpDummyData(Group group, List<Group> groups, MembershipInfo membershipInfo, Set<MembershipInfo> membersToAdd) {
        membersToAdd.add(membershipInfo);
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER);
        groups.add(group);
        group.setDescription("This is a test group");
    }
}
