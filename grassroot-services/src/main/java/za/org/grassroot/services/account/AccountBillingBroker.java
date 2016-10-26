package za.org.grassroot.services.account;

import java.util.Set;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    void calculateAccountStatements(boolean sendEmails, boolean sendNotifications);

    void processAccountStatementEmails(Set<String> billingRecordUids);

}
