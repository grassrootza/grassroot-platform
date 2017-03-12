package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    Account loadPrimaryAccountForUser(String userUid, boolean loadEvenIfDisabled);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts(boolean visibleOnly, AccountPaymentType paymentMethod, AccountBillingCycle billingCycle);

    String createAccount(String userUid, String accountName, String billedUserUid, AccountType accountType,
                         AccountPaymentType accountPaymentType, AccountBillingCycle billingCycle, boolean enableFreeTrial);

    void enableAccount(String userUid, String accountUid, String ongoingPaymentRef, AccountPaymentType paymentType,
                       boolean ensureUserAddedToAdmin, boolean setBillingUser);

    // note: should only adjust billing date if this is called at end of successful payment cycle
    void updateAccountPaymentCycleAndMethod(String userUid, String accountUid, AccountPaymentType paymentType,
                                            AccountBillingCycle billingCycle, boolean adjustNextBillingDate);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void setAccountPrimary(String userUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole, boolean generateClosingBill);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void closeAccount(String userUid, String accountUid, boolean generateClosingBill);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addAdministrator(String userUid, String accountUid, String administratorUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid, boolean preventRemovingSelfOrBilling);

    void changeAccountType(String userUid, String accountUid, AccountType newAccountType, Set<String> groupsToRemove);

    // the next two should only be called by system admin, administrators can only do type switch
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void updateAccountGroupLimits(String userUid, String accountUid, int numberOfGroups, int maxSizePerGroup,
                                  int maxDepth, int messagesPerMonth, int todosPerMonth);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void updateAccountCardPaymentReference(String userUid, String accountUid, String paymentRef);

    /* Methods to work out some limits left on account */

    Map<AccountType, Integer> getNumberGroupsPerType();

    Map<AccountType, Integer> getNumberMessagesPerType();

    Map<AccountType, Integer> getGroupSizeLimits();

    Map<AccountType, Integer> getAccountTypeFees();

    /* Some methods to facilitate testing */

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void resetAccountBillingDates(Instant commonInstant);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateAccountBalance(String adminUid, String accountUid, long newBalance);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateAccountFee(String adminUid, String accountUid, long newFee);

}
