package za.org.grassroot.services;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;
import za.org.grassroot.core.util.AfterTxCommitTask;

@Component
public class TransactionalEventsHandler {
    @TransactionalEventListener()
    public void handleAfterTxCommitTask(AfterTxCommitTask task) {
        task.run();
    }
}
