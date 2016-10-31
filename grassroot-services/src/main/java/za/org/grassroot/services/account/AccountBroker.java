package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.enums.AccountType;

import java.time.LocalDate;
import java.util.List;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts(boolean visibleOnly);

    String createAccount(String userUid, String accountName, String billedUserUid, AccountType accountType);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void enableAccount(String userUid, String accountUid, LocalDate nextStatementDate);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void makeAccountInvisible(String userUid, String accountUid);

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

}
