package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;

import java.util.List;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    PaidGroup loadPaidGroup(String paidGroupUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts();

    // Methods to create institutional accounts, designate administrators and deactivate them
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    String createAccount(String userUid, String accountName, String administratorUid, String billingEmail);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addAdministrator(String userUid, String accountUid, String administratorUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void updateBillingEmail(String userUid, String accountUid, String billingEmail);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void updateSettings(Account changedAccount);

    // Methods to designate groups as paid for by accounts (and remove the designation)
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeGroupFromAccount(String accountUid, String paidGroupUid, String removingUserUid);

    Account findAccountForGroup(String groupUid);

    // Methods to handle additional features for accounts
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void sendFreeFormMessage(String userUid, String groupUid, String message);
}
