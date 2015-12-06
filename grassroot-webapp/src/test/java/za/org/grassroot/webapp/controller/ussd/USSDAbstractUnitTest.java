package za.org.grassroot.webapp.controller.ussd;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.web.servlet.HandlerExceptionResolver;
import org.springframework.web.servlet.ViewResolver;
import org.springframework.web.servlet.handler.SimpleMappingExceptionResolver;
import org.springframework.web.servlet.view.InternalResourceViewResolver;
import za.org.grassroot.services.EventLogManagementService;
import za.org.grassroot.services.EventManagementService;
import za.org.grassroot.services.GroupManagementService;
import za.org.grassroot.services.UserManagementService;
import za.org.grassroot.webapp.util.USSDGroupUtil;
import za.org.grassroot.webapp.util.USSDUtil;

import java.util.List;
import java.util.Properties;

/**
 * Created by luke on 2015/11/20.
 */
@RunWith(MockitoJUnitRunner.class)
public abstract class USSDAbstractUnitTest {

    protected MockMvc mockMvc;

    @Mock
    protected UserManagementService userManagementServiceMock;

    @Mock
    protected GroupManagementService groupManagementServiceMock;

    @Mock
    protected EventManagementService eventManagementServiceMock;

    @Mock
    protected EventLogManagementService eventLogManagementServiceMock;

    @InjectMocks
    protected USSDGroupUtil ussdGroupUtil;

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

        // viewResolver.setViewClass
        viewResolver.setPrefix("/pages");
        viewResolver.setSuffix(".html");

        return viewResolver;
    }

    protected MessageSource messageSource() {
        // todo: wire this up to the actual message source so unit tests make sure languages don't have gaps ...
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }

    protected void wireUpMessageSourceAndGroupUtil(USSDController controller, USSDGroupUtil groupUtil) {
        controller.setMessageSource(messageSource());
        groupUtil.setMessageSource(messageSource());
        controller.setUssdGroupUtil(groupUtil);
    }
}
