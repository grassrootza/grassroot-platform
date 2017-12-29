package za.org.grassroot.webapp.controller.rest;


import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.GcmRegistration;
import za.org.grassroot.core.enums.DeliveryRoute;
import za.org.grassroot.integration.messaging.JwtService;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by paballo on 2016/05/17.
 */
public class GcmRestControllerTest extends RestAbstractUnitTest {

    @Mock
    private JwtService jwtServiceMock;

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
        when(jwtServiceMock.getUserIdFromJwtToken("jwt_token")).thenReturn(sessionTestUser.getUid());
        when(userManagementServiceMock.load(sessionTestUser.getUid())).thenReturn(sessionTestUser);
        when(gcmRegistrationBrokerMock.registerUser(sessionTestUser, "random")).thenReturn(gcmRegistration);
        mockMvc.perform(post(path + "register")
                .header("Authorization", "Bearer jwt_token")
                .param("gcmToken", "random")).andExpect(status().isOk());
        verify(jwtServiceMock).getUserIdFromJwtToken("jwt_token");
        verify(userManagementServiceMock).load(sessionTestUser.getUid());
        verify(gcmRegistrationBrokerMock).registerUser(sessionTestUser,"random");
        verify(userManagementServiceMock).setMessagingPreference(sessionTestUser.getUid(), DeliveryRoute.ANDROID_APP);
        verifyNoMoreInteractions(userManagementServiceMock);
        verifyNoMoreInteractions(gcmRegistrationBrokerMock);

    }

}