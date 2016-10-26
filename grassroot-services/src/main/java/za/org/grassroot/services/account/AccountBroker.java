package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.enums.AccountType;

import java.util.List;
import java.util.Map;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

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

    Map<AccountType, Long> fetchAccountTypesAndFees();

}
