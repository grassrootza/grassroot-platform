package za.org.grassroot.services.account;

import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Account;
import za.org.grassroot.core.domain.Group;

import java.util.List;
import java.util.Set;

/**
 * Created by luke on 2016/10/25.
 */
public interface AccountGroupBroker {

    boolean isGroupOnAccount(String groupUid);

    Account findAccountForGroup(String groupUid);

    List<Group> fetchGroupsSponsoredByAccount(String accountUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    int addUserCreatedGroupsToAccount(String accountUid, String userUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    List<Group> candidateGroupsForAccount(String userUid, String accountUid, String filterTerm);

    boolean canAddGroupToAccount(String userUid);

    boolean canAddMultipleGroupsToOwnAccount(String userUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void removeGroupsFromAccount(String accountUid, Set<String> groupUid, String removingUserUid);


    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void sendFreeFormMessage(String userUid, String groupUid, String message);

    int numberGroupsLeft(String accountUid);

    int numberTodosLeftForGroup(String groupUid);
}
