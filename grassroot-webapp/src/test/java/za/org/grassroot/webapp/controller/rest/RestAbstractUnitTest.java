package za.org.grassroot.webapp.controller.rest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.*;

/**
 * Created by paballo on 2016/02/18.
 *
 *
 */
@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public class RestAbstractUnitTest
{
    protected final static String testUserPhone = "27815550000";
    protected final static User sessionTestUser = new User("testUser", testUserPhone);

    protected MockMvc mockMvc;
    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;

    @Mock
    protected UserManagementService userManagementServiceMock;
    @Mock
    protected EventManagementService eventManagementServiceMock;

    protected MessageSource messageSource() {

        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }
    @Test
    public void dummyTest() throws Exception{

    }

}
