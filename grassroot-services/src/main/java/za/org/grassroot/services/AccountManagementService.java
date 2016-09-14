package za.org.grassroot.services;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;

import java.util.List;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountManagementService {

    Account loadAccount(String accountUid);

    PaidGroup loadPaidGroup(String paidGroupUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts();

    /*
    Methods to create institutional accounts, designate administrators and deactivate them
     */

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    String createAccount(String userUid, String accountName, String administratorUid, String billingEmail);

    void addAdministrator(String userUid, String accountUid, String administratorUid);

    void updateBillingEmail(String userUid, String accountUid, String billingEmail);

    void updateSettings(Account changedAccount);

    /*
    Methods to designate groups as paid for by accounts (and remove the designation)
     */

    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    void removeGroupFromAccount(String accountUid, String paidGroupUid, String removingUserUid);

    Account findAccountForGroup(String groupUid);

    /*
    Methods to handle additional features for accounts
     */

    void sendFreeFormMessage(String userUid, String groupUid, String message);
}
