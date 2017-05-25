package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.enums.UserMessagingPreference;

import static org.mockito.Mockito.*;
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
        when(userManagementServiceMock.findByInputNumber(testUserPhone)).thenReturn(sessionTestUser);
        when(gcmRegistrationBrokerMock.registerUser(sessionTestUser, "random")).thenReturn(gcmRegistration);
        mockMvc.perform(post(path + "register/{phoneNumber}/{code}", testUserPhone, testUserCode).param("registration_id", "random")).andExpect(status().isOk());
        verify(userManagementServiceMock).findByInputNumber(testUserPhone);
        verify(gcmRegistrationBrokerMock).registerUser(sessionTestUser,"random");
        verify(gcmRegistrationBrokerMock).refreshAllGroupTopicSubscriptions(sessionTestUser.getUid(), "random");
        verify(userManagementServiceMock).setMessagingPreference(sessionTestUser.getUid(), UserMessagingPreference.ANDROID_APP);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(gcmRegistrationBrokerMock);

    }

    // todo : complete this
    // @Test
    // public void gcmDeregistrationShouldWork() throws Exception{
    // when(userManagementServiceMock.loadOrCreateUser(testUserPhone)).thenReturn();
    //   when(gcmRegistrationBrokerMock.registerUser(sessionTestUser, "random"));

    // }

}