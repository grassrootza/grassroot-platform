package za.org.grassroot.webapp.controller.ussd;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.runners.MockitoJUnitRunner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.services.UserManagementService;

import java.util.Properties;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Created by luke on 2015/11/20.
 */
@RunWith(MockitoJUnitRunner.class)
public class USSDHomeControllerTest extends USSDAbstractUnitTest {

    private static final Logger log = LoggerFactory.getLogger(USSDHomeControllerTest.class);

    @InjectMocks
    USSDHomeController ussdHomeController;

    @Before
    public void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(ussdHomeController)
                .setHandlerExceptionResolvers(exceptionResolver())
                .setValidator(validator())
                .setViewResolvers(viewResolver())
                .build();
    }

    @Test
    public void welcomeMenuShouldWork() throws Exception {

        ussdHomeController.setMessageSource(messageSource());

        User testUser = new User("27810001111");
        testUser.setLanguageCode("en");

        when(userManagementServiceMock.loadOrSaveUser("27810001111")).thenReturn(testUser);

        mockMvc.perform(get("/ussd/start").param("msisdn", "27810001111")).andExpect(status().isOk());

        // USSDMenu welcomeMenu = ussdHomeController.welcomeMenu("Hello", testUser);

    }

}
