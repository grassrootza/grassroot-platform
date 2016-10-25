package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;
import za.org.grassroot.core.enums.AccountType;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    PaidGroup loadPaidGroup(String paidGroupUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts();

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    String createAccount(String userUid, String accountName, String administratorUid, String billingEmail, AccountType accountType);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid, String reasonToRecord);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addAdministrator(String userUid, String accountUid, String administratorUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void updateBillingEmail(String userUid, String accountUid, String billingEmail);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void changeAccountType(String userUid, String accountUid, AccountType accountType);

    // the next two should only be called by system admin, administrators can only do type switch
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void updateAccountGroupLimits(String userUid, String accountUid, Integer numberOfGroups, Integer maxSizePerGroup, Integer maxDepth);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN')")
    void updateAccountMessageSettings(String userUid, String accountUid, Boolean freeFormEnabled, Integer costPerMessage);

    // Methods to designate groups as paid for by accounts (and remove the designation)
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeGroupFromAccount(String accountUid, String groupUid, String removingUserUid);

    Account findAccountForGroup(String groupUid);

    // Methods to handle additional features for accounts
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void sendFreeFormMessage(String userUid, String groupUid, String message);

    /*
    METHODS TO HANDLE COMPUTING COSTS & BILLS (FOR SCHEDULED JOBS)
     */

    Map<Account, Long> calculateMonthlyStatements(Instant startValidity, Instant endValidity);

    long calculateAccountCostsInPeriod(String accountUid, Instant periodStart, Instant periodEnd, boolean generateLog);
}
