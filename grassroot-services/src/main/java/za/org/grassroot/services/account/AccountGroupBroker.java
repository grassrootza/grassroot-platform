package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.PaidGroup;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountGroupBroker {

    PaidGroup loadPaidGroup(String paidGroupUid);

    // Methods to designate groups as paid for by accounts (and remove the designation)
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeGroupFromAccount(String accountUid, String groupUid, String removingUserUid);

    Account findAccountForGroup(String groupUid);

    // Methods to handle additional features for accounts
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void sendFreeFormMessage(String userUid, String groupUid, String message);

}
