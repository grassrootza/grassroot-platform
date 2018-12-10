package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.dto.group.GroupRefDTO;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Created by luke on 2015/11/12.
 */
public interface AccountBroker {

    Account loadAccount(String accountUid);

    Account loadDefaultAccountForUser(String userUid);

    void validateUserCanViewAccount(String accountUid, String userUid);

    String createAccount(String userUid, String accountName, String billedUserUid, String billingEmail, String ongoingPaymentRef);

    void setAccountSubscriptionRef(String userUid, String accountUid, String subscriptionId);

    void setAccountPaymentRef(String userUid, String accountUid, String paymentRef);

    void setLastBillingDate(String userUid, String accountUid, Instant newLastBillingDate);

    void enableAccount(String userUid, String accountUid, String logMessage);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void setAccountPrimary(String userUid, String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void disableAccount(String administratorUid, String accountUid, String reasonToRecord);

    void addAdministrator(String userUid, String accountUid, String administratorUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid, boolean preventRemovingSelfOrBilling);

    void addGroupsToAccount(String accountUid, Set<String> groupUid, String addingUserUid);

    void addAllUserCreatedGroupsToAccount(String accountUid, String userUid);

    Set<Group> fetchGroupsUserCanAddToAccount(String accountUid, String userUid);

    void removeGroupsFromAccount(String accountUid, Set<String> groupUid, String removingUserUid);

    GroupRefDTO fetchGroupAccountInfo(String userUid, String groupUid);

    void renameAccount(String adminUid, String accountUid, String accountName);

    @PreAuthorize("hasAnyRole('ROLE_ACCOUNT_ADMIN, ROLE_SYSTEM_ADMIN')")
    void closeAccount(String userUid, String accountUid, String closingReason);

    long countAccountNotifications(String accountUid, Instant startTime, Instant endTime);

    long countChargedNotificationsForGroup(String accountUid, String groupUid, Instant startTime, Instant endTime);

    long countChargedUssdSessionsForAccount(String accountUid, Instant startTime, Instant endTime);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    List<Account> loadAllAccounts(boolean enabledOnly);

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    Map<String, String> loadDisabledAccountMap();

    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    void updateDataSetLabels(String userUid, String accountUid, String dataSetLabels, boolean updateReferenceTables);

    DataSetInfo fetchDataSetInfo(String userUid, String dataSetLabel, Instant start, Instant end);

}
