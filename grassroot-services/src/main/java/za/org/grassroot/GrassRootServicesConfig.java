package za.org.grassroot;

import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.orm.jpa.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.data.jpa.convert.threeten.Jsr310JpaConverters;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jms.annotation.EnableJms;
import org.springframework.jms.config.JmsListenerContainerFactory;
import org.springframework.jms.config.SimpleJmsListenerContainerFactory;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;

import javax.jms.ConnectionFactory;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * @author Lesetse Kimwaga
 */

@Configuration
@ComponentScan("za.org.grassroot")
@EntityScan(basePackageClasses = {GrassRootServicesConfig.class, Jsr310JpaConverters.class})
@EnableAutoConfiguration
@EnableJpaRepositories
@EnableJms
@EnableAsync
@EnableScheduling
public class GrassRootServicesConfig  implements SchedulingConfigurer {

    @Bean
    JmsListenerContainerFactory<?> messagingJmsContainerFactory(ConnectionFactory connectionFactory) {
        SimpleJmsListenerContainerFactory factory = new SimpleJmsListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        return factory;
    }

    @Bean( name = "servicesMessageSource")
    public ResourceBundleMessageSource messageSource() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("notification-messages/messages");
        return source;
    }

    @Bean ( name = "servicesMessageSourceAccessor")
    public MessageSourceAccessor getMessageSourceAccessor() { return new MessageSourceAccessor(messageSource()); }

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        taskRegistrar.setScheduler(taskExecutor());
    }

    @Bean(destroyMethod="shutdown")
    public Executor taskExecutor() {
        return Executors.newScheduledThreadPool(10);
    }

}
