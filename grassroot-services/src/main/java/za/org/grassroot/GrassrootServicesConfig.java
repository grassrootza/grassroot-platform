package za.org.grassroot;

import org.quartz.CronTrigger;
import org.quartz.JobDetail;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.orm.jpa.EntityScan;
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
import org.springframework.scheduling.quartz.CronTriggerFactoryBean;
import org.springframework.scheduling.quartz.JobDetailFactoryBean;
import org.springframework.scheduling.quartz.SchedulerFactoryBean;
import za.org.grassroot.scheduling.ApplicationContextAwareQuartzJobBean;
import za.org.grassroot.scheduling.BatchedNotificationSenderJob;
import za.org.grassroot.scheduling.UnreadNotificationSenderJob;

import java.util.Properties;
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

	@Bean
	public JobDetailFactoryBean batchedNotificationSenderJobDetail() {
		JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
		factoryBean.setJobClass(BatchedNotificationSenderJob.class);
		factoryBean.setDurability(false);
		return factoryBean;
	}

	@Bean
	public CronTriggerFactoryBean batchedNotificationSenderCronTrigger(
			@Qualifier("batchedNotificationSenderJobDetail") JobDetail jobDetail) {
		CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
		factoryBean.setJobDetail(jobDetail);
		factoryBean.setCronExpression("0/15 * * * * ?");
		factoryBean.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
		return factoryBean;
	}

	@Bean
	public JobDetailFactoryBean unreadNotificationSenderJobDetail() {
		JobDetailFactoryBean factoryBean = new JobDetailFactoryBean();
		factoryBean.setJobClass(UnreadNotificationSenderJob.class);
		factoryBean.setDurability(false);
		return factoryBean;
	}

	@Bean
	public CronTriggerFactoryBean unreadNotificationSenderCronTrigger(
			@Qualifier("unreadNotificationSenderJobDetail") JobDetail jobDetail) {
		CronTriggerFactoryBean factoryBean = new CronTriggerFactoryBean();
		factoryBean.setJobDetail(jobDetail);
		factoryBean.setCronExpression("0 0/5 * * * ?");
		factoryBean.setMisfireInstruction(CronTrigger.MISFIRE_INSTRUCTION_DO_NOTHING);
		return factoryBean;
	}

	@Bean
	public SchedulerFactoryBean schedulerFactoryBean(@Qualifier("batchedNotificationSenderCronTrigger") CronTrigger sendTrigger,
													 @Qualifier("unreadNotificationSenderCronTrigger") CronTrigger unreadTrigger) {
		Properties quartzProperties = new Properties();

		SchedulerFactoryBean factory = new SchedulerFactoryBean();
		factory.setAutoStartup(true);
		factory.setSchedulerName("grassroot-quartz");
		factory.setWaitForJobsToCompleteOnShutdown(true);
		factory.setQuartzProperties(quartzProperties);
		factory.setStartupDelay(10);
		factory.setApplicationContextSchedulerContextKey(ApplicationContextAwareQuartzJobBean.APPLICATION_CONTEXT_KEY);
		factory.setTriggers(sendTrigger, unreadTrigger);

		return factory;
	}

}