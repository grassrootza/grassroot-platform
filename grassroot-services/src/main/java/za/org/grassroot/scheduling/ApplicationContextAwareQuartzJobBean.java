package za.org.grassroot.scheduling;

import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.quartz.SchedulerContext;
import org.quartz.SchedulerException;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.quartz.QuartzJobBean;

public abstract class ApplicationContextAwareQuartzJobBean extends QuartzJobBean {

	public static final String APPLICATION_CONTEXT_KEY = "applicationContext";

	protected ApplicationContext getApplicationContext(JobExecutionContext context) throws JobExecutionException {
		try {
			SchedulerContext schedulerContext = context.getScheduler().getContext();
			return (ApplicationContext) schedulerContext.get(APPLICATION_CONTEXT_KEY);
		} catch (SchedulerException e) {
			throw new JobExecutionException(e);
		}
	}

}
