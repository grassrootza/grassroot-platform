package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountBillingRecord;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    AccountBillingRecord generateSignUpBill(final String accountUid);

    AccountBillingRecord generatePaymentChangeBill(final String accountUid, final long amountToCharge);

    void generateClosingBill(String userUid, final String accountUid);

    // for use when doing an account shift, etc
    void generateBillOutOfCycle(String accountUid, boolean generateStatement, boolean triggerPayment, Long forceAmount, boolean addToBalance);

    void calculateStatementsForDueAccounts(boolean sendEmails, boolean sendNotifications);

    void processAccountStatement(Account account, AccountBillingRecord generatingBill, boolean sendEmail);

    void processBillsDueForPayment();

    // load all the bills for an account, with the unpaidOnly allowing a filter on that status
    List<AccountBillingRecord> loadBillingRecords(String accountUid, boolean unpaidOnly, Sort sort);

    AccountBillingRecord fetchRecordByPayment(String paymentId);

    List<AccountBillingRecord> findRecordsInSameStatementCycle(String recordUid);

    List<AccountBillingRecord> findRecordsWithStatementDates(String accountUid);

    void togglePaymentStatus(String recordUid);

    /*
    Three methods used to control billing & payment flow by sys admin
     */

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void forceUpdateBillingDate(String adminUid, String accountUid, LocalDateTime nextBillingDate);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void haltAccountPayments(String adminUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void changeBillPaymentDate(String adminUid, String recordUid, LocalDateTime paymentDate);

}
