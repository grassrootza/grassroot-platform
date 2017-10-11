package za.org.grassroot.services.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

@Component
public class AccountBrokerBaseImpl {

    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public void setLogsAndNotificationsBroker(LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    protected void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    protected void storeAccountLogPostCommit(AccountLog accountLog) {
        AfterTxCommitTask task = () -> createAndStoreSingleAccountLog(accountLog);
        eventPublisher.publishEvent(task);
    }

}
