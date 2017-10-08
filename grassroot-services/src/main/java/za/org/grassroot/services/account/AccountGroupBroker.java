package za.org.grassroot.services.account;

import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import za.org.grassroot.core.domain.Group;
import za.org.grassroot.core.domain.NotificationTemplate;
import za.org.grassroot.core.domain.account.Account;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
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

    int numberEventsLeftForGroup(String groupUid);

    int numberEventsLeftForParent(String eventUid);

    /**
     * Methods for doing and handling group welcome methods
     */
    @PreAuthorize("hasAnyRole('ROLE_SYSTEM_ADMIN', 'ROLE_ACCOUNT_ADMIN')")
    void createGroupWelcomeMessages(String userUid, String accountUid, String groupUid, List<String> messages,
                                    Duration delayToSend, Locale language, boolean onlyViaFreeChannels);

    NotificationTemplate loadTemplate(String groupUid);

    void generateGroupWelcomeNotifications(String addingUserUid, String groupUid, Set<String> addedMemberMsisdns);

}
