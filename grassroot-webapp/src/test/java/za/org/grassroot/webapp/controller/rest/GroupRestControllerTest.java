package za.org.grassroot.webapp.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Sets;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.GroupJoinMethod;
import za.org.grassroot.core.domain.GroupLog;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.webapp.controller.android1.GroupQueryRestController;
import za.org.grassroot.webapp.controller.android1.GroupRestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
/**
 * Created by Siyanda Mzam on 2016/03/16.
 */

public class GroupRestControllerTest extends RestAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(GroupRestControllerTest.class);

    @InjectMocks
    private GroupRestController groupRestController;

    @InjectMocks
    private GroupQueryRestController groupQueryRestController;

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
        membersToAdd.add(new MembershipInfo("27810001234", BaseRoles.ROLE_ORDINARY_MEMBER, "test user"));
        GroupLog groupLog = new GroupLog(testGroup, sessionTestUser, GroupLogType.GROUP_MEMBER_ADDED_AT_CREATION,
                sessionTestUser, null, null, "");

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(groupBrokerMock.create(sessionTestUser.getUid(), testGroupName, null, membersToAdd,
                                    GroupPermissionTemplate.DEFAULT_GROUP, testEventDescription, null, true, false)).thenReturn(testGroup);
        when(groupQueryBrokerMock.getMostRecentLog(testGroup)).thenReturn(groupLog);

        log.info("Mock set up for : userUid={}, name={}, members={}, desc={}", sessionTestUser.getUid(), testGroupName,
                 membersToAdd, testEventDescription);

        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(membersToAdd);

        mockMvc.perform(post(path + "create/{phoneNumber}/{code}/{groupName}/{description}",
                             testUserPhone, testUserCode, testGroupName, testEventDescription)
                                .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk());

        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(groupBrokerMock).create(sessionTestUser.getUid(), testGroupName, null, membersToAdd, GroupPermissionTemplate.DEFAULT_GROUP, meetingEvent.getDescription(), null, true, false);
        verify(groupQueryBrokerMock, times(1)).getMostRecentLog(testGroup);
        verify(groupBrokerMock, times(1)).checkForDuplicate(sessionTestUser.getUid(), testGroupName);
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }


    private void settingUpDummyData(Group group, List<Group> groups, MembershipInfo membershipInfo, Set<MembershipInfo> membersToAdd) {
        membersToAdd.add(membershipInfo);
        group.addMember(sessionTestUser, BaseRoles.ROLE_GROUP_ORGANIZER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
        groups.add(group);
        group.setDescription(testEventDescription);
    }
}
