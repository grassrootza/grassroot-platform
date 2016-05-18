package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.enums.UserMessagingPreference;
import za.org.grassroot.integration.services.GcmService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by paballo on 2016/05/17.
 */
public class GcmRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private GcmRestController gcmRestController;

    private String path = "/api/gcm/";


   @Before
   public void setUp(){
       mockMvc = MockMvcBuilders.standaloneSetup(gcmRestController).build();
   }


    @Test
    public void gcmRegistrationShouldWork() throws  Exception{
        GcmRegistration gcmRegistration = new GcmRegistration();
        when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn(sessionTestUser);
        when(gcmServiceMock.registerUser(sessionTestUser, "random")).thenReturn(gcmRegistration);
        mockMvc.perform(post(path + "register/{phoneNumber}/{code}", testUserPhone, testUserCode).param("registration_id", "random")).andExpect(status().isCreated());
        verify(userManagementServiceMock).loadOrSaveUser(testUserPhone);
        verify(gcmServiceMock).registerUser(sessionTestUser,"random");
        verify(userManagementServiceMock).setMessagingPreference(sessionTestUser.getUid(), UserMessagingPreference.ANDROID_APP);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(gcmServiceMock);

    }

    @Test
    public void gcmDeregistrationShouldWork() throws Exception{
       // when(userManagementServiceMock.loadOrSaveUser(testUserPhone)).thenReturn();
     //   when(gcmServiceMock.registerUser(sessionTestUser, "random"));

    }

}