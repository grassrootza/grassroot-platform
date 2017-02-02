package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.AccountBillingRecord;
import za.org.grassroot.core.enums.AccountPaymentType;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountBillingBroker {

    void setAccountPaymentType(String accountUid, AccountPaymentType paymentType);

    AccountBillingRecord generateSignUpBill(final String accountUid);

    AccountBillingRecord generatePaymentChangeBill(final String accountUid, final long amountToCharge);

    void generateClosingBill(String userUid, final String accountUid);

    // for use when doing an account shift, etc
    void generateBillOutOfCycle(String accountUid, boolean generateStatement, boolean triggerPayment, Long forceAmount, boolean addToBalance);

    void calculateStatementsForDueAccounts(boolean sendEmails, boolean sendNotifications);

    void processAccountStatement(Account account, AccountBillingRecord generatingBill, boolean sendEmail);

    List<AccountBillingRecord> findRecordsWithStatementDates(String accountUid);

    List<AccountBillingRecord> findRecordsInSameStatementCycle(String recordUid);

    /**
     *
     * @param accountUid Optional : specify an account to load
     * @param unpaidOnly Whether to load only unpaid or unpaid and paid
     * @param sort A sort
     * @return
     */
    List<AccountBillingRecord> loadBillingRecords(String accountUid, boolean unpaidOnly, Sort sort);

    AccountBillingRecord fetchRecordByPayment(String paymentId);

    void togglePaymentStatus(String recordUid);

    /*
    Three methods used to control billing & payment flow by sys admin
     */

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void forceUpdateBillingCycle(String adminUid, String accountUid, LocalDateTime nextBillingDate);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void haltAccountPayments(String adminUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void changeBillPaymentDate(String adminUid, String recordUid, LocalDateTime paymentDate);

}
