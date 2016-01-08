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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import org.thymeleaf.spring4.expression.Mvc;

import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.LogBookService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.Application;
import za.org.grassroot.webapp.MVCConfig;
import za.org.grassroot.webapp.controller.BaseController;

import org.springframework.boot.test.SpringApplicationConfiguration;
import org.springframework.boot.test.SpringApplicationContextLoader;


/*
 * @author Paballo Ditshego
 */


@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@SpringApplicationConfiguration(classes = Application.class)
public abstract class WebAppAbstractUnitTest {
	
	protected MockMvc mockMvc;



    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;
    
    @Mock
    protected LogBookService logBookServiceMock;
    
    @Autowired
    protected WebApplicationContext webApplicationContext;
   
    
    
    
    
    protected void setUp(){
    	mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
    	
    }
    
    
    protected void setUp(BaseController baseController){
    	mockMvc = MockMvcBuilders.standaloneSetup(baseController).build();
    	
    }
    
    
   

	
	

}
