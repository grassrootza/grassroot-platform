package za.org.grassroot.webapp.controller.rest;

import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.association.GroupJoinRequest;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.services.ChangedSinceData;
import za.org.grassroot.webapp.controller.android1.GroupQueryRestController;

import java.util.*;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by Siyanda Mzam on 2016/03/16.
 */

public class GroupQueryRestControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(GroupQueryRestControllerTest.class);

    @InjectMocks
    private GroupQueryRestController groupQueryRestController;

    private String path = "/api/group/";

    private List<Group> groups = new ArrayList<>();
    private MembershipInfo membershipInfo = new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, sessionTestUser.getDisplayName());
    private Set<MembershipInfo> membersToAdd = Sets.newHashSet();
    private Event event = meetingEvent;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(groupQueryRestController).build();
        ReflectionTestUtils.setField(groupQueryRestController, "ussdDialCode", "*134*1994*");
    }

    @Test
    public void getUserGroupsShouldWork() throws Exception {
        GroupLog groupLog = new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_ADDED,
                sessionTestUser, null, null, null);
        testGroup.addMember(sessionTestUser, "ROLE_GROUP_ORGANIZER", GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        List<Group> groups = Collections.singletonList(testGroup);

        ChangedSinceData<Group> wrapper = new ChangedSinceData<>(groups, Collections.EMPTY_SET);

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupQueryBrokerMock.getActiveGroups(sessionTestUser, null)).thenReturn(wrapper);

        when(eventBrokerMock.getMostRecentEvent(testGroup.getUid())).thenReturn(event);
        when(groupQueryBrokerMock.getMostRecentLog(testGroup)).thenReturn(groupLog);
        mockMvc.perform(get(path + "list/{phoneNumber}/{code}", testUserPhone, testUserCode)).andExpect(status().is2xxSuccessful());

	    verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(groupQueryBrokerMock).getActiveGroups(sessionTestUser, null);
	    verify(eventBrokerMock).getMostRecentEvent(testGroup.getUid());
        verify(groupQueryBrokerMock).getMostRecentLog(testGroup);
    }

    @Test
    public void searchForGroupsShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupQueryBrokerMock.findGroupFromJoinCode(testSearchTerm)).thenReturn(Optional.of(testGroup));
        mockMvc.perform(get(path + "search/{phoneNumber}/{code}", testUserPhone, testUserCode).param("searchTerm", testSearchTerm)).andExpect(status().is2xxSuccessful());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupQueryBrokerMock).findGroupFromJoinCode(testSearchTerm);
    }

    @Test
    public void searchRequestToJoinGroup() throws Exception {
        GroupJoinRequest testRequest = new GroupJoinRequest(sessionTestUser, testGroup, "please let me in");
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
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groups.add(group);
        group.setDescription(testEventDescription);
    }
}
