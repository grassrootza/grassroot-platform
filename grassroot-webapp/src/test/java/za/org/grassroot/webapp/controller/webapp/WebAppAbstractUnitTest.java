package za.org.grassroot.webapp.controller.webapp;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;

import java.util.ArrayList;


/*
 * Paballo Ditshego
 */


@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public abstract class WebAppAbstractUnitTest {

    protected final static String testUserPhone = "27815550000";
    protected final static User sessionTestUser = new User("testUser", testUserPhone);

    protected MockMvc mockMvc;
    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;
    @Mock
    protected LogBookService logBookServiceMock;
    @Mock
    protected UserManagementService userManagementServiceMock;
    @Mock
    protected GroupManagementService groupManagementServiceMock;
    @Mock
    protected EventManagementService eventManagementServiceMock;

    protected void setUp(BaseController baseController) {
        mockAuthentication();
        baseController.setMessageSource(messageSource());
        mockMvc = MockMvcBuilders.standaloneSetup(baseController).build();

    }

    protected void mockAuthentication() {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(sessionTestUser, "12345", new ArrayList<GrantedAuthority>());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }


    private MessageSource messageSource() {

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }




}
