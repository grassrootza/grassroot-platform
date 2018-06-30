package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.enums.AccountType;

import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    Account loadPrimaryAccountForUser(String userUid, boolean loadEvenIfDisabled);

    String createAccount(String userUid, String accountName, String billedUserUid, String ongoingPaymentRef);

    void setAccountSubscriptionRef(String userUid, String accountUid, String subscriptionId);

    void setAccountPaymentRef(String userUid, String accountUid, String paymentRef);

    void enableAccount(String userUid, String accountUid, String ongoingPaymentRef, boolean ensureUserAddedToAdmin, boolean setBillingUser);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void setAccountPrimary(String userUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole, boolean generateClosingBill);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addAdministrator(String userUid, String accountUid, String administratorUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid, boolean preventRemovingSelfOrBilling);

    void addGroupsToAccount(String accountUid, Set<String> groupUid, String addingUserUid);

    void addAllUserCreatedGroupsToAccount(String accountUid, String userUid);

    Map<AccountType, Integer> getAccountTypeFees();

    void modifyAccount(String adminUid, String accountUid, AccountType accountType,
                       String accountName, String billingEmail);

    void closeAccount(String userUid, String accountUid, String closingReason);

}
