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

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan(basePackageClasses = {GrassrootServicesConfig.class, Jsr310JpaConverters.class})
@EnableAutoConfiguration @EnableJpaRepositories @EnableAsync @EnableScheduling
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
		source.setFallbackToSystemLocale(true);
		return source;
	}

	@Bean(name = "servicesMessageSourceAccessor")
	public MessageSourceAccessor getMessageSourceAccessor() { return new MessageSourceAccessor(messageSource()); }

}