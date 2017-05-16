package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.data.domain.PageImpl;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.integration.location.UssdLocationServicesBroker;
import za.org.grassroot.services.group.GroupPermissionTemplate;
import za.org.grassroot.webapp.util.USSDUrlUtil;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveAddressMenu;
import static za.org.grassroot.webapp.util.USSDUrlUtil.saveSafetyMenuPrompt;


/**
 * Created by paballo on 2016/07/21.
 */
public class USSDSafetyGroupControllerTest extends USSDAbstractUnitTest {

    private static final String testUserPhone = "27801110000";
    private static final String testUser2Phone = "27833403013";
    private static final String testUserUid = "test-user-unique-id";
    private static final String phoneParam = "msisdn";
    private static final String groupParam = "groupUid";

    private static final String joinCode ="*134*1994*33";

    private static final String path = "/ussd/safety/";

    private User testUser;
    private User testUser2;
    private Group testGroup;
    private SafetyEvent safetyEvent;
    private Set<MembershipInfo> testMembers = new HashSet<>();
    private GroupPermissionTemplate template = GroupPermissionTemplate.DEFAULT_GROUP;

    @Mock
    private UssdLocationServicesBroker locationServicesBrokerMock;

    @InjectMocks
    private USSDSafetyGroupController ussdSafetyGroupController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ussdSafetyGroupController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();

        wireUpMessageSourceAndGroupUtil(ussdSafetyGroupController);

        testUser = new User(testUserPhone);
        testUser2 = new User(testUser2Phone);
        testGroup = new Group("test group", testUser);
        testGroup.setGroupTokenCode(joinCode);
        safetyEvent = new SafetyEvent(testUser2,testGroup);
        testMembers.add(new MembershipInfo(testUserPhone, BaseRoles.ROLE_GROUP_ORGANIZER, null));
    }

    @Test
    public void openingSafetyGroupShouldWorkWithoutGroup() throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupQueryBrokerMock.fetchUserCreatedGroups(testUser, 0, 1)).thenReturn(new PageImpl<Group>(Collections.singletonList(testGroup)));
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verify(groupQueryBrokerMock, times(1)).fetchUserCreatedGroups(testUser, 0, 1);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void createGroupShouldWorkWhenNameIsInvalid() throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get(path + "create").param(phoneParam, testUserPhone).param(userChoiceParam, "1")).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verifyZeroInteractions(groupBrokerMock);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void createGroupShouldWorkWithValidName() throws Exception{
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(groupBrokerMock.create(testUser.getUid(),testGroup.getName(),null,testMembers,template,null,null,false)).thenReturn(testGroup);
        mockMvc.perform(get(path + "create").param(phoneParam, testUserPhone).param(userChoiceParam, "test group")).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone);
        verify(groupBrokerMock,times(1)).create(testUser.getUid(),testGroup.getName(),null,testMembers,template,null,null,false);
        verify(safetyEventBrokerMock,times(1)).setSafetyGroup(testUser.getUid(),testGroup.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);

    }


    @Test
    public void openingSafetyGroupMenuShouldWorkWithGroup() throws Exception{
        testUser.setSafetyGroup(testGroup);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(addressBrokerMock.hasAddress(testUserUid)).thenReturn(false);
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(addressBrokerMock, times(1)).hasAddress(testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

   @Test
    public void addAddressShouldWorkWhenFieldIsNull() throws Exception{
       final String urlToSave = USSDUrlUtil.saveSafetyMenuPrompt("add-address");
       when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
       mockMvc.perform(get(path +"add-address").param(phoneParam,testUserPhone)).andExpect(status().isOk());
       verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
       verifyZeroInteractions(addressBrokerMock);
       verifyZeroInteractions(groupBrokerMock);
       verifyZeroInteractions(safetyEventBrokerMock);
       verifyNoMoreInteractions(userManagementServiceMock);

   }

    @Test
    public void addAddressShouldWorkWhenFieldIsHouse() throws Exception{
        final String urlToSave = USSDUrlUtil.saveAddressMenu("add-address", "house");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path + "add-address").param(phoneParam,testUserPhone).param(userChoiceParam,"44").param("field","house")).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(addressBrokerMock,times(1)).updateUserAddress(testUser.getUid(),"44",null,null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
        verifyZeroInteractions(groupBrokerMock);
    }

    @Test
    public void addAddressShouldWorkWhenFieldIsStreet() throws Exception{
        final String urlToSave = USSDUrlUtil.saveAddressMenu("add-address", "street");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        mockMvc.perform(get(path+"add-address").param(phoneParam,testUserPhone).param(userChoiceParam,"Stanley").param("field","street")).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
       verify(addressBrokerMock,times(1)).updateUserAddress(testUser.getUid(),null,"Stanley",null);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
        verifyZeroInteractions(groupBrokerMock);
    }

    @Test
    public void addAddressShouldWorkWhenFieldIsTown() throws Exception{
        final String urlToSave = USSDUrlUtil.saveAddressMenu("add-address", "town");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(addressBrokerMock.getUserAddress(testUser.getUid())).thenReturn(new Address(testUser,"44","Stanley", "JHB", true));
        mockMvc.perform(get(path+"add-address").param(phoneParam,testUserPhone).param(userChoiceParam,"JHB").param("field","town")).andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(addressBrokerMock,times(1)).updateUserAddress(testUser.getUid(),null,null,"JHB");
        verify(addressBrokerMock,times(1)).getUserAddress(testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
        verifyZeroInteractions(groupBrokerMock);
    }

    @Test
    public void pickingExistingGroupShouldWork() throws Exception{
        final String urlToSave = USSDUrlUtil.saveSafetyGroupMenu("pick-group-do", testGroup.getUid(), null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);

        mockMvc.perform(get(path+"pick-group-do").param(phoneParam,testUserPhone).param(groupParam,testGroup.getUid())).andExpect(status().isOk());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(groupBrokerMock,times(1)).load(testGroup.getUid());
        verify(safetyEventBrokerMock,times(1)).setSafetyGroup(testUser.getUid(),testGroup.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(groupBrokerMock);
    }

    @Test
    public void changeAddressShouldWorkWhenFieldIsStreet() throws Exception{
        final String urlToSave = saveAddressMenu("change-address-do", "street");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(addressBrokerMock.getUserAddress(testUser.getUid())).thenReturn(new Address(testUser,"38","Stanley", "JHB", true));

        mockMvc.perform(get(path+"change-address-do").param(phoneParam,testUserPhone).param("field","street").
                param(userChoiceParam,"Stanley")).andExpect(status().isOk());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(addressBrokerMock,times(1)).updateUserAddress(testUser.getUid(),null,"Stanley",null);
        verify(addressBrokerMock,times(1)).getUserAddress(testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
    }

    @Test
    public void changeAddressShouldWorkWhenFieldIsHouse() throws Exception{
        final String urlToSave = saveAddressMenu("change-address-do", "house");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(addressBrokerMock.getUserAddress(testUser.getUid())).thenReturn(new Address(testUser,"38","Stanley", "JHB", true));

        mockMvc.perform(get(path+"change-address-do").param(phoneParam,testUserPhone).param("field", "house").
                param(userChoiceParam,"38")).andExpect(status().isOk());

        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(addressBrokerMock,times(1)).updateUserAddress(testUser.getUid(),"38",null,null);
        verify(addressBrokerMock,times(1)).getUserAddress(testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
    }

    @Test
    public void removeAddressShouldWork() throws Exception{
        final String urlToSave = saveSafetyMenuPrompt("remove-address-do");
        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);

        mockMvc.perform(get(path+"remove-address-do").param(phoneParam,testUserPhone)).andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(addressBrokerMock,times(1)).removeAddress(testUser.getUid());
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(addressBrokerMock);
        verifyZeroInteractions(groupBrokerMock);

    }

    @Test
    public void recordResponseShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get(path + "record-response-do").param(phoneParam, testUserPhone)
                .param("entityUid", safetyEvent.getUid())
                .param("confirmed", String.valueOf(true))).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);

    }

    @Test
    public void resetShouldWork() throws Exception {
        testUser.setSafetyGroup(testGroup);
        final String urlToSave = saveSafetyMenuPrompt("reset-do");

        when(userManagementServiceMock.findByInputNumber(testUserPhone, urlToSave)).thenReturn(testUser);
        when(groupBrokerMock.load(testGroup.getUid())).thenReturn(testGroup);
        mockMvc.perform(get(path+"reset-do").param(phoneParam,testUserPhone)
                .param(groupParam, testGroup.getUid()))
                .andExpect(status().isOk());
        verify(userManagementServiceMock,times(1)).findByInputNumber(testUserPhone, urlToSave);
        verify(safetyEventBrokerMock,times(1)).resetSafetyGroup(testUser.getUid(),false);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(safetyEventBrokerMock);
    }

}
