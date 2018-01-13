package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserResponseBroker;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Created by luke on 2015/11/28.
 */
public class USSDUserControllerTest extends USSDAbstractUnitTest {

    private static final String testUserPhone = "27801115555";
    private static final String phoneParam = "msisdn";
    private static final String path = "/ussd/user/";

    private User testUser;

    @Mock private UserResponseBroker userResponseBrokerMock;

    @InjectMocks
    USSDHomeController ussdHomeController;

    @InjectMocks
    USSDUserController ussdUserController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ussdUserController, ussdHomeController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
        wireUpHomeController(ussdHomeController);
        wireUpMessageSourceAndGroupUtil(ussdUserController);
        testUser = new User(testUserPhone, null, null);
    }

    /*
    User rename should work properly
     */
    @Test
    public void userRenamePromptShouldWork() throws Exception {
        testUser.setHasInitiatedSession(true);
        testUser.setDisplayName("");

        when(userManagementServiceMock.loadOrCreateUser(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.needsToRenameSelf(testUser)).thenReturn(true);

        mockMvc.perform(get("/ussd/start").param(phoneParam, testUserPhone)).
                andExpect(status().isOk());

        testUser.setDisplayName("now it is set"); // necessary else when/then doesn't work within controller

        mockMvc.perform(get("/ussd/rename-start").param(phoneParam, testUserPhone).param("request", "now it is set")).
                andExpect(status().isOk());

        verify(userManagementServiceMock, times(1)).loadOrCreateUser(testUserPhone);
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).updateDisplayName(testUser.getUid(), testUser.getUid(), "now it is set");

    }

    @Test
    public void startMenuShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        mockMvc.perform(get(path + "start").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void renameSelfPromptShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        User namedUser = new User("27801115550", "named", null);
        when(userManagementServiceMock.findByInputNumber(namedUser.getPhoneNumber())).thenReturn(namedUser);
        mockMvc.perform(get(path + "name").param(phoneParam, testUserPhone)).andExpect(status().isOk());
        mockMvc.perform(get(path + "name").param(phoneParam, namedUser.getPhoneNumber())).andExpect(status().isOk());
        verify(userManagementServiceMock, times(2)).findByInputNumber(anyString());
        verifyNoMoreInteractions(groupBrokerMock);
        verifyNoMoreInteractions(eventBrokerMock);
    }

    @Test
    public void renameSelfDoneScreenShouldWork() throws Exception {
        User namedUser = new User("278011115550", "named", null);
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        when(userManagementServiceMock.findByInputNumber(namedUser.getPhoneNumber())).thenReturn(namedUser);
        mockMvc.perform(get(path + "name-do").param(phoneParam, testUserPhone).param("request", "naming")).
                andExpect(status().isOk());
        mockMvc.perform(get(path + "name-do").param(phoneParam, namedUser.getPhoneNumber()).param("request", "new name")).
                andExpect(status().isOk());
        verify(userManagementServiceMock, times(1)).findByInputNumber(testUserPhone);
        verify(userManagementServiceMock, times(1)).findByInputNumber(namedUser.getPhoneNumber());
        verify(userManagementServiceMock, times(1)).updateDisplayName(testUser.getUid(), testUser.getUid(), "naming");
        verify(userManagementServiceMock, times(1)).updateDisplayName(namedUser.getUid(), namedUser.getUid(), "new name");
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyZeroInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void changeLanguageMenuShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);
        // todo : restore this ...
        verifyZeroInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

    @Test
    public void changeLanguageConfirmShouldWork() throws Exception {
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(testUser);

        verifyZeroInteractions(groupBrokerMock);
        verifyZeroInteractions(eventBrokerMock);
    }

}
