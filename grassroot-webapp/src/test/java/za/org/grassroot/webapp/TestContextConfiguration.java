package za.org.grassroot.webapp;

import org.springframework.context.annotation.Configuration;

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
