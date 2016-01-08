package za.org.grassroot.webapp.controller.webapp;

import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import za.org.grassroot.services.*;
import za.org.grassroot.webapp.controller.BaseController;


/*
 * @author Paballo Ditshego
 */


@RunWith(MockitoJUnitRunner.class)
@ContextConfiguration
public abstract class WebAppAbstractUnitTest {
	
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


    protected final static String testUserPhone = "27815550000";

    
    
    protected void setUp(BaseController baseController){

        mockAuthentication();
    	mockMvc = MockMvcBuilders.standaloneSetup(baseController).build();
    	
    }

    protected void mockAuthentication(){


         UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(testUserPhone, "12345");
        SecurityContextHolder.getContext().setAuthentication(authentication);


    }


    
    
   

	
	

}
