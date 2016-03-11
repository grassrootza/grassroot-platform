package za.org.grassroot.webapp.controller.rest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectWriter;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.Before;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.webapp.enums.RestMessage;
import za.org.grassroot.webapp.enums.RestStatus;
import za.org.grassroot.webapp.model.rest.ResponseWrappers.ResponseWrapperImpl;

import java.nio.charset.Charset;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


/**
 * Created by paballo on 2016/03/09.
 */
public class UserRestControllerTest extends RestAbstractUnitTest {

    @InjectMocks
    UserRestController userRestController;

    private static final String testtokenCode = "b1b64a1b-a374-45a1-806b-2abe01a08ac6";
    private static final String path =  "/api/user/";
    public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(), MediaType.APPLICATION_JSON.getSubtype(), Charset.forName("utf8"));
    ObjectMapper mapper = new ObjectMapper();
    ObjectWriter ow = null;


    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userRestController).build();
        mapper.configure(SerializationFeature.WRAP_ROOT_VALUE, false);
        ow = mapper.writer();
    }

    @Test
    public void addShouldWorkWhenUserDoesNotExist() throws Exception {

        ResponseWrapperImpl restResponeWrapper = new ResponseWrapperImpl(HttpStatus.OK, RestMessage.VERIFICATION_TOKEN_SENT,
                RestStatus.SUCCESS);
        when(userManagementServiceMock.userExist(testUserPhone)).thenReturn(false);
        when(userManagementServiceMock.generateAndroidUserVerifier(testUserPhone)).thenReturn(testUserPhone);
        mockMvc.perform(get(path+"add/{phoneNumber}",testUserPhone).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk()).andExpect(content().string(ow.writeValueAsString(restResponeWrapper)));
        verify(userManagementServiceMock,times(1)).userExist(testUserPhone);
        verify(userManagementServiceMock,times(1)).generateAndroidUserVerifier(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);
    }

    @Test
    public void addShouldWorkWhenUserExists() throws Exception{

        ResponseWrapperImpl restResponeWrapper = new ResponseWrapperImpl(HttpStatus.CONFLICT, RestMessage.USER_ALREADY_EXISTS,
                RestStatus.FAILURE);
        when(userManagementServiceMock.userExist(testUserPhone)).thenReturn(true);
        mockMvc.perform(get(path+"add/{phoneNumber}",testUserPhone).accept(MediaType.APPLICATION_JSON))
                .andExpect(status().is4xxClientError()).andExpect(content().string(ow.writeValueAsString(restResponeWrapper)));
        verify(userManagementServiceMock,times(1)).userExist(testUserPhone);
        verifyNoMoreInteractions(userManagementServiceMock);

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
        when(passwordTokenServiceMock.generateLongLivedCode(sessionTestUser)).thenReturn(testVerifivationTokenCode);
        mockMvc.perform(post(path+"verify/{code}", testVerifivationTokenCode.getCode()).contentType(APPLICATION_JSON_UTF8).content(requestJson))
                .andExpect(status().isOk());




    }*/




}
