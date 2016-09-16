package za.org.grassroot.webapp.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.time.Instant;
import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Created by Siyanda Mzam on 2016/03/16.
 */

public class GroupRestControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(GroupRestControllerTest.class);

    @InjectMocks
    private GroupRestController groupRestController;

    private String path = "/api/group/";
    private List<Group> groups = new ArrayList<>();
    private MembershipInfo membershipInfo = new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getDisplayName());
    private Set<MembershipInfo> membersToAdd = Sets.newHashSet();
    private Event event = meetingEvent;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(groupRestController).build();
    }

    @Test
    public void createGroupShouldWork() throws Exception {

        settingUpDummyData(testGroup, groups, membershipInfo, membersToAdd);
        GroupLog groupLog = new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_MEMBER_ADDED, 0L, "");
        membersToAdd.add(new MembershipInfo("27810001234", BaseRoles.ROLE_ORDINARY_MEMBER, "test user"));

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.create(sessionTestUser.getUid(), testGroupName, null, membersToAdd,
                                    GroupPermissionTemplate.DEFAULT_GROUP, testEventDescription, null, true)).thenReturn(testGroup);
        when(groupBrokerMock.getMostRecentLog(testGroup)).thenReturn(groupLog);

        log.info("Mock set up for : userUid={}, name={}, members={}, desc={}", sessionTestUser.getUid(), testGroupName,
                 membersToAdd, testEventDescription);

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(membersToAdd);

        mockMvc.perform(post(path + "create/{phoneNumber}/{code}/{groupName}/{description}",
                             testUserPhone, testUserCode, testGroupName, testEventDescription)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(groupBrokerMock).create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, meetingEvent.getDescription(), null, true);
        verify(groupBrokerMock, times(1)).getMostRecentLog(testGroup);
        verify(groupBrokerMock, times(1)).checkForDuplicate(sessionTestUser.getUid(), testGroupName);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void getUserGroupsShouldWork() throws Exception {
        GroupLog groupLog = new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_ADDED, sessionTestUser.getId());
        testGroup.addMember(sessionTestUser, "ROLE_GROUP_ORGANIZER");
        List<Group> groups = Collections.singletonList(testGroup);

        ChangedSinceData<Group> wrapper = new ChangedSinceData<>(groups, Collections.EMPTY_SET);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(wrapper);

        when(eventBrokerMock.getMostRecentEvent(testGroup.getUid())).thenReturn(event);
        when(groupBrokerMock.getMostRecentLog(testGroup)).thenReturn(groupLog);
        mockMvc.perform(get(path + "list/{phoneNumber}/{code}", testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());

	    verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(groupBrokerMock).getActiveGroups(sessionTestUser, null);
	    verify(eventBrokerMock).getMostRecentEvent(testGroup.getUid());
        verify(groupBrokerMock).getMostRecentLog(testGroup);
    }

    @Test
    public void searchForGroupsShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.findGroupFromJoinCode(testSearchTerm)).thenReturn(Optional.of(testGroup));
        mockMvc.perform(get(path + "search/{phoneNumber}/{code}", testUserPhone, testUserCode).param("searchTerm", testSearchTerm)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupBrokerMock).findGroupFromJoinCode(testSearchTerm);
    }

    @Test
    public void searchRequestToJoinGroup() throws Exception {
        GroupJoinRequest testRequest = new GroupJoinRequest(sessionTestUser, testGroup, Instant.now(), "please let me in");
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupJoinRequestServiceMock.open(sessionTestUser.getUid(), testGroup.getUid(), "please let me in")).thenReturn(testRequest.getUid());
        when(groupJoinRequestServiceMock.loadRequest(testRequest.getUid())).thenReturn(testRequest);
        mockMvc.perform(post(path + "join/request/{phoneNumber}/{code}", testUserPhone, testUserCode)
                .param("uid", testGroup.getUid()).param("message", "please let me in")).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(groupJoinRequestServiceMock).open(sessionTestUser.getUid(), testGroup.getUid(), "please let me in");
    }

    private void settingUpDummyData(Group group, List<Group> groups, MembershipInfo membershipInfo, Set<MembershipInfo> membersToAdd) {
        membersToAdd.add(membershipInfo);
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER);
        groups.add(group);
        group.setDescription(testEventDescription);
    }
}
