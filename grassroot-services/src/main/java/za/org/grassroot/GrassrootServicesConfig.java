package za.org.grassroot;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.spring4.SpringTemplateEngine;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;
import org.thymeleaf.templateresolver.ITemplateResolver;
import org.thymeleaf.templateresolver.StringTemplateResolver;

import java.util.Collections;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan(basePackageClasses = {GrassrootServicesConfig.class, Jsr310JpaConverters.class})
@EnableAutoConfiguration
@EnableJpaRepositories
@EnableAsync
@EnableScheduling
public class GrassrootServicesConfig implements SchedulingConfigurer {

	/*
	Configuration for scheduled tasks
	 */
    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod="shutdown")
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(10);
    }

	/*
	Configuration for notification messages and HTML emails
	 */
	@Bean(name = "servicesMessageSource")
	public ResourceBundleMessageSource messageSource() {
		ResourceBundleMessageSource source = new ResourceBundleMessageSource();
		source.setBasename("notification-messages/messages");
		return source;
	}

	@Bean(name = "servicesMessageSourceAccessor")
	public MessageSourceAccessor getMessageSourceAccessor() { return new MessageSourceAccessor(messageSource()); }

	@Bean
	public ResourceBundleMessageSource htmlEmailSource() {
		final ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
		messageSource.setBasename("mail/messages");
		return messageSource;
	}

	@Bean(name = "emailTemplateEngine")
	public TemplateEngine emailTemplateEngine() {
		final SpringTemplateEngine templateEngine = new SpringTemplateEngine();
		templateEngine.addTemplateResolver(textTemplateResolver());
		templateEngine.addTemplateResolver(htmlTemplateResolver());
		templateEngine.addTemplateResolver(stringTemplateResolver());
		templateEngine.setTemplateEngineMessageSource(htmlEmailSource());
		return templateEngine;
	}

	private ITemplateResolver textTemplateResolver() {
		final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
		templateResolver.setOrder(1);
		templateResolver.setResolvablePatterns(Collections.singleton("text/*"));
		templateResolver.setPrefix("/mail/");
		templateResolver.setSuffix(".txt");
		templateResolver.setTemplateMode(TemplateMode.TEXT);
		templateResolver.setCharacterEncoding("UTF-8");
		templateResolver.setCacheable(false);
		return templateResolver;
	}

	private ITemplateResolver htmlTemplateResolver() {
		final ClassLoaderTemplateResolver templateResolver = new ClassLoaderTemplateResolver();
		templateResolver.setOrder(2);
		templateResolver.setResolvablePatterns(Collections.singleton("html/*"));
		templateResolver.setPrefix("/mail/");
		templateResolver.setSuffix(".html");
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setCharacterEncoding("UTF-8");
		templateResolver.setCacheable(false);
		return templateResolver;
	}

	private ITemplateResolver stringTemplateResolver() {
		final StringTemplateResolver templateResolver = new StringTemplateResolver();
		templateResolver.setOrder(3);
		templateResolver.setTemplateMode(TemplateMode.HTML);
		templateResolver.setCacheable(false);
		return templateResolver;
	}

}