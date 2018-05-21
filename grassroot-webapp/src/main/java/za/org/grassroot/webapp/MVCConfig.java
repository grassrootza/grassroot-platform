package za.org.grassroot.webapp;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurerAdapter;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import za.org.grassroot.webapp.interceptor.SimpleLoggingInterceptor;
import za.org.grassroot.webapp.interceptor.TokenValidationInterceptor;

import javax.servlet.Filter;
import java.util.Locale;

/**
 *
 * Application  MVC infrastructure configuration
 * @author Lesetse Kimwaga
 */

@Configuration
@ControllerAdvice @Slf4j
public class MVCConfig extends WebMvcConfigurerAdapter {

    @Bean
    public Filter characterEncodingFilter() {
        CharacterEncodingFilter filter = new CharacterEncodingFilter();
        filter.setEncoding("UTF-8");
        filter.setForceEncoding(true);
        return filter;
    }

    @Bean
    public TokenValidationInterceptor tokenValidationInterceptor() {
        return new TokenValidationInterceptor();
    }

    @Bean
    public SimpleLoggingInterceptor loggingInterceptor(){return  new SimpleLoggingInterceptor();}

    @Bean
    @Profile({"localpg", "staging"})
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurerAdapter() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/**")
                        .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                        .allowedOrigins("*").allowedMethods("*");
            }
        };
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(localeChangeInterceptor());
        registry.addInterceptor(loggingInterceptor());
        registry.addInterceptor(tokenValidationInterceptor())
                .addPathPatterns("/api/group/**")
                .addPathPatterns("/api/user/profile/**")
                .addPathPatterns("/api/task/**")
                .addPathPatterns("/api/vote/**")
                .addPathPatterns("/api/meeting/**")
                .addPathPatterns("/api/notification")
                .excludePathPatterns("/api/group/search")
                .excludePathPatterns("/api/group/fetch/flyer")
                .excludePathPatterns("/api/group/outside/join/**")
                .excludePathPatterns("/api/language/test/**")
                .excludePathPatterns("/api/jwt/public/credentials")
                .excludePathPatterns("/api/news/list")
                .excludePathPatterns("/api/user/profile/image/**")
                .excludePathPatterns("/api/activity/list");
    }

    @Bean (name = "messageSource")
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        return source;
    }

    @Bean ( name = "messageSourceAccessor")
    public MessageSourceAccessor getMessageSourceAccessor()
    {
        return  new MessageSourceAccessor(messageSource());
    }

    @Bean
    public LocaleResolver localeResolver() {
        SessionLocaleResolver sessionLocaleResolver = new SessionLocaleResolver();
        sessionLocaleResolver.setDefaultLocale(Locale.UK);
        return sessionLocaleResolver;
    }

    @Bean
    public LocaleChangeInterceptor localeChangeInterceptor() {
        LocaleChangeInterceptor LocaleChangeInterceptor = new LocaleChangeInterceptor();
        LocaleChangeInterceptor.setParamName("language");
        return LocaleChangeInterceptor;
    }

}
