package za.org.grassroot.webapp.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.webapp.controller.android1.UserRestController;


/**
 * Created by paballo on 2016/03/09.
 */
public class UserRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    private UserRestController userRestController;

    private ObjectMapper mapper = new ObjectMapper();

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userRestController).build();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
    }

    @Test
    public void addShouldWorkWhenUserDoesNotExist() throws Exception {

       /* ResponseWrapperImpl restResponeWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT,
                RestStatus.SUCCESS);
        when(userManagementServiceMock.userExist(testUserPhone)).thenReturn(false);
        when(userManagementServiceMock.generateAndroidUserVerifier(testUserPhone)).thenReturn(testUserPhone);
        mockMvc.perform(get(path+"add/{phoneNumber}",testUserPhone).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content().string(ow.writeValueAsString(restResponeWrapper)));
        verify(userManagementServiceMock,times(1)).userExist(testUserPhone);
        verify(userManagementServiceMock,times(1)).generateAndroidUserVerifier(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);*/
    }

    @Test
    public void addShouldWorkWhenUserExists() throws Exception{

      /*  ResponseWrapperImpl restResponeWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS,
                RestStatus.FAILURE);
        when(userManagementServiceMock.userExist(testUserPhone)).thenReturn(true);
        mockMvc.perform(get(path+"add/{phoneNumber}",testUserPhone).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError()).andExpect(content().string(ow.writeValueAsString(restResponeWrapper)));
        verify(userManagementServiceMock,times(1)).userExist(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);*/

    }

 /*   @Test
    public void verifyShouldWorkWhenCodeIsValid() throws Exception{

        UserDTO testUserDTo = new UserDTO(testUserPhone,"testUser");
        VerificationTokenCode testVerifivationTokenCode = new VerificationTokenCode(testUserPhone,testtokenCode);
        TokenResponseWrapper restResponeWrapper = new TokenResponseWrapper(HttpStatus.OK ,RestMessage.USER_REGISTRATION_SUCCESSFUL,
                RestStatus.SUCCESS, testVerifivationTokenCode);
        String requestJson=ow.writeValueAsString(testUserDTo);
        System.out.println(requestJson);
        when(passwordTokenServiceMock.isVerificationCodeValid(testUserDTo,testtokenCode)).thenReturn(true);
        when(userManagementServiceMock.createAndroidUserProfile(testUserDTo)).thenReturn(sessionTestUser);
        when(passwordTokenServiceMock.generateLongLivedAuthCode(sessionTestUser)).thenReturn(testVerifivationTokenCode);
        mockMvc.perform(post(path+"verify/{code}", testVerifivationTokenCode.getCode()).contentType(APPLICATION_JSON_UTF8).content(requestJson))
                .andExpect(status().isOk());




    }*/




}
