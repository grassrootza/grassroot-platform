package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import za.org.grassroot.core.domain.AccountBillingRecord;

import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    AccountBillingRecord generateSignUpBill(final String accountUid);

    AccountBillingRecord generatePaymentChangeBill(final String accountUid, final long amountToCharge);

    void generateClosingBill(String userUid, final String accountUid);

    void calculateAccountStatements(boolean sendEmails, boolean sendNotifications);

    void processAccountStatementEmails(Set<String> billingRecordUids);

    List<AccountBillingRecord> fetchBillingRecords(final String accountUid, final Sort sort);

    AccountBillingRecord fetchRecordByPayment(String paymentId);

}
