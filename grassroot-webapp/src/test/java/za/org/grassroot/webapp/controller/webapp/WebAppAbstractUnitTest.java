package za.org.grassroot.webapp.controller.webapp;

import java.util.Properties;

import org.apache.catalina.security.SecurityConfig;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.powermock.modules.junit4.PowerMockRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.MessageSource;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;

import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.MVCConfig;

/*
 * @author Paballo Ditshego
 */


@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration



@ContextConfiguration (classes = {MVCConfig.class,SecurityConfig.class})
public class WebAppAbstractUnitTest {
	
	protected MockMvc mockMvc;

    @Mock
    protected UserManagementService userManagementServiceMock;

    @Mock
    protected GroupManagementService groupManagementServiceMock;

    @Mock
    protected EventManagementService eventManagementServiceMock;

    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;
    
    @Mock
    protected LogBookService logBookServiceMock;
   
    
    
    
    
    @Autowired
    @Qualifier("messageSource")
    MessageSource messageSource;
    
    
    
    protected HandlerExceptionResolver exceptionResolver() {
        SimpleMappingExceptionResolver exceptionResolver = new SimpleMappingExceptionResolver();
        Properties statusCodes = new Properties();

        statusCodes.put("error/404", "404");
        statusCodes.put("error/error", "500");

        exceptionResolver.setStatusCodes(statusCodes);
        return exceptionResolver;
    }

    protected LocalValidatorFactoryBean validator() {
        return new LocalValidatorFactoryBean();
    }

    protected ViewResolver viewResolver() {
    	
        InternalResourceViewResolver viewResolver = new InternalResourceViewResolver();

       
        viewResolver.setPrefix("/pages");
        viewResolver.setSuffix(".html");

        return viewResolver;
    }
    
    
    

	protected MessageSource getMessageSource() {
		return messageSource;
	}

	
	

}
