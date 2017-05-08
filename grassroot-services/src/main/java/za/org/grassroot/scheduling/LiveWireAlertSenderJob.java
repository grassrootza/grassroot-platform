package za.org.grassroot.scheduling;

import org.quartz.DisallowConcurrentExecution;
import org.quartz.JobExecutionContext;
import org.quartz.JobExecutionException;
import org.springframework.context.ApplicationContext;
import za.org.grassroot.services.livewire.BatchedLiveWireSender;

/**
 * Created by luke on 2017/05/08.
 * As with BatchedNotificationSender, this is instantiated by Quartz, not Spring, so have to manage
 * dependencies ourselves
 */
@DisallowConcurrentExecution
public class LiveWireAlertSenderJob extends ApplicationContextAwareQuartzJobBean {
    @Override
    protected void executeInternal(JobExecutionContext context) throws JobExecutionException {
        ApplicationContext applicationContext = getApplicationContext(context);
        BatchedLiveWireSender batchedLiveWireSender = applicationContext.getBean(BatchedLiveWireSender.class);
        batchedLiveWireSender.processPendingLiveWireAlerts();
    }
}
