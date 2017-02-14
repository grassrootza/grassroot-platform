package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
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

    void addGroupToAccount(String accountUid, String groupUid, String addingUserUid);

    void addGroupsToAccount(String accountUid, Set<String> groupUid, String addingUserUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    int addUserCreatedGroupsToAccount(String accountUid, String userUid);

    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    List<Group> searchGroupsForAddingToAccount(String userUid, String accountUid, String filterTerm);

    List<Group> fetchUserCreatedGroupsUnpaidFor(String userUid, Sort sort);

    boolean canAddGroupToAccount(String userUid, String accountUid);

    boolean canAddAllCreatedGroupsToAccount(String userUid, String accountUid);

    void removeGroupsFromAccount(String accountUid, Set<String> groupUid, String removingUserUid);

    void sendFreeFormMessage(String userUid, String groupUid, String message);

    int numberGroupsLeft(String accountUid);

    int numberTodosLeftForGroup(String groupUid);

    int numberMessagesLeft(String accountUid);
}
