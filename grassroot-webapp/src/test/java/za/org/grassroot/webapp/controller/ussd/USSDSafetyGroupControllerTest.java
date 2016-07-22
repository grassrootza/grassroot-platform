package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.MembershipInfo;
import za.org.grassroot.services.enums.GroupPermissionTemplate;

import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Created by paballo on 2016/07/21.
 */
public class USSDSafetyGroupControllerTest extends USSDAbstractUnitTest {

    private static final String testUserPhone = "27801110000";
    private static final String testUserUid = "test-user-unique-id";
    private static final String phoneParam = "msisdn";
    private static final String groupParam = "groupUid";
    private String testGroupIdString;
    private static final String joinCode ="*134*1994*33";

    private static final String path = "/ussd/safety/";

    private User testUser;
    private Group testGroup;
    private Set<MembershipInfo> testMembers = new HashSet<>();
    private GroupPermissionTemplate template = GroupPermissionTemplate.DEFAULT_GROUP;


    @InjectMocks
    USSDSafetyGroupController ussdSafetyGroupController;

    @Before
    public void setUp() {

        mockMvc = MockMvcBuilders.standaloneSetup(ussdSafetyGroupController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpMessageSourceAndGroupUtil(ussdSafetyGroupController, ussdGroupUtil);
        testUser = new User(testUserPhone);
        testGroup = new Group("test group", testUser);
        testGroup.setGroupTokenCode(joinCode);
        testMembers.add(new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, null));
        testGroupIdString = testGroup.getUid();

    }

    @Test
    public void openingSafetyGroupShouldWorkWithoutGroup() throws Exception{

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupBrokerMock);

    }


    @Test
    public void openingSafetyGroupMenuShouldWorkWithGroup() throws Exception{

        testUser.setSafetyGroupUid(testGroupIdString);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.hasAddress(testUserUid)).thenReturn(false);
        when(groupBrokerMock.load(testGroupIdString)).thenReturn(testGroup);
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).hasAddress(testUser.getUid());
        verify(groupBrokerMock,times(1)).load(testGroupIdString);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }

    @Test
    public void createSafetyGroupShouldWorkWhenUserNoSafetyGroup() throws Exception{

        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);



    }









}
