package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;

import java.util.List;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    AccountBillingRecord generateSignUpBill(final String accountUid);

    AccountBillingRecord generatePaymentChangeBill(final String accountUid, final long amountToCharge);

    void generateClosingBill(String userUid, final String accountUid);

    // for use when doing an account shift, etc
    void generateBillOutOfCycle(String accountUid, boolean generateStatement, boolean triggerPayment);

    void calculateStatementsForDueAccounts(boolean sendEmails, boolean sendNotifications);

    void processAccountStatement(Account account, AccountBillingRecord generatingBill, boolean sendEmail);

    List<AccountBillingRecord> findRecordsWithStatementDates(String accountUid);

    List<AccountBillingRecord> findRecordsInSameStatementCycle(String recordUid);

    AccountBillingRecord fetchRecordByPayment(String paymentId);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void resetAccountStatementDatesForTesting();

}
