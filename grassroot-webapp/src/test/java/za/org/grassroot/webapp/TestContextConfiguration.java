package za.org.grassroot.webapp;

import org.mockito.Mockito;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import za.org.grassroot.services.UserManagementService;

import java.util.Locale;

/**
 * @author Lesetse Kimwaga
 */
@Configuration
public class TestContextConfiguration {

    // the below seem unnecessary given how we are doing things now, but leaving unless cause arises to delete

    /*@Bean(name = "messageSource")
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();

        messageSource.setBasename("messages");
        messageSource.setUseCodeAsDefaultMessage(true);

        return messageSource;
    }

    @Bean ( name = "messageSourceAccessor")
    public MessageSourceAccessor getMessageSourceAccessor()
    {
        return  new MessageSourceAccessor(messageSource());
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver sessionLocaleResolver = new SessionLocaleResolver();
        sessionLocaleResolver.setDefaultLocale(Locale.UK); //defaulting back to proper English, not American
        return sessionLocaleResolver;
    }


    @Bean
    public UserManagementService userManagementService() {
        return Mockito.mock(UserManagementService.class);
    }*/

}
