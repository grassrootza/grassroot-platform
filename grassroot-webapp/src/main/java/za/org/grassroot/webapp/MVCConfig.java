package za.org.grassroot.webapp;

import com.github.mxab.thymeleaf.extras.dataattribute.dialect.DataAttributeDialect;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.core.Ordered;
import org.springframework.http.CacheControl;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.CharacterEncodingFilter;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.i18n.LocaleChangeInterceptor;
import org.springframework.web.servlet.i18n.SessionLocaleResolver;
import org.thymeleaf.extras.java8time.dialect.Java8TimeDialect;
import za.org.grassroot.services.user.PasswordTokenService;
import za.org.grassroot.webapp.interceptor.SimpleLoggingInterceptor;
import za.org.grassroot.webapp.interceptor.TokenValidationInterceptor;

import javax.servlet.Filter;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 *
 * Application  MVC infrastructure configuration
 * @author Lesetse Kimwaga
 */

@Configuration
@ControllerAdvice @Slf4j
public class MVCConfig extends WebMvcConfigurerAdapter {

    private PasswordTokenService passwordTokenService;

    @Bean
    public DataAttributeDialect dataAttributeDialect() {
        return new DataAttributeDialect();
    }

    @Bean
    public Java8TimeDialect java8TimeDialect() { return new Java8TimeDialect(); }

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

    @Autowired
    public void setPasswordTokenService(PasswordTokenService passwordTokenService) {
        this.passwordTokenService = passwordTokenService;
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
                .excludePathPatterns("/api/jwt/public/credentials");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        CacheControl staticCache = CacheControl.maxAge(60, TimeUnit.DAYS);

        registry.addResourceHandler("/css/**").addResourceLocations("classpath:/static/css/")
                .setCacheControl(staticCache);
        registry.addResourceHandler("/assets/**").addResourceLocations("classpath:/static/assets/")
                .setCacheControl(staticCache);
        registry.addResourceHandler("/images/**").addResourceLocations("classpath:/static/images/")
                .setCacheControl(staticCache);
        registry.addResourceHandler("/fonts/**").addResourceLocations("classpath:/static/fonts/")
                .setCacheControl(staticCache);
        registry.addResourceHandler("/js/**").addResourceLocations("classpath:/static/js/")
                .setCacheControl(staticCache);
    }

    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        registry.addViewController("/login").setViewName("login");
        registry.setOrder(Ordered.HIGHEST_PRECEDENCE);
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
