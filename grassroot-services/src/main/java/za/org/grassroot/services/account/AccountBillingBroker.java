package za.org.grassroot.services.account;

import java.time.Instant;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    void calculateAccountStatements(Instant periodStart, Instant periodEnd, boolean sendEmails, boolean sendNotifications);

    long calculateAccountCostsInPeriod(String accountUid, Instant periodStart, Instant periodEnd);

}
