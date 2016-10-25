package za.org.grassroot.services.account;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.GroupAccountMismatchException;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.exception.GroupNotPaidForException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.Objects;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountGroupBrokerImpl implements AccountGroupBroker {

    private UserRepository userRepository;
    private GroupRepository groupRepository;
    private PermissionBroker permissionBroker;
    private AccountRepository accountRepository;
    private PaidGroupRepository paidGroupRepository;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public AccountGroupBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, PermissionBroker permissionBroker,
                                  AccountRepository accountRepository, PaidGroupRepository paidGroupRepository, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.permissionBroker = permissionBroker;
        this.accountRepository = accountRepository;
        this.paidGroupRepository = paidGroupRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public PaidGroup loadPaidGroup(String paidGroupUid) {
        return paidGroupRepository.findOneByUid(paidGroupUid);
    }

    @Override
    @Transactional
    public void addGroupToAccount(String accountUid, String groupUid, String addingUserUid) throws GroupAlreadyPaidForException {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(addingUserUid);

        Group group = groupRepository.findOneByUid(groupUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User addingUser = userRepository.findOneByUid(addingUserUid);

        if (!account.getAdministrators().contains(addingUser)) {
            permissionBroker.validateSystemRole(addingUser, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if (group.isPaidFor()) {
            throw new GroupAlreadyPaidForException();
        }

        PaidGroup paidGroup = new PaidGroup(group, account, addingUser);
        paidGroupRepository.saveAndFlush(paidGroup);
        account.addPaidGroup(paidGroup);
        group.setPaidFor(true);
        storeGroupAddOrRemoveLogs(AccountLogType.GROUP_ADDED, account, group, paidGroup.getUid(), addingUser);
    }

    @Override
    @Transactional(readOnly =  true)
    public Account findAccountForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        if (!group.isPaidFor())
            return null;
        else
            return paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group).getAccount();
    }

    @Override
    @Transactional
    public void removeGroupFromAccount(String accountUid, String groupUid, String removingUserUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(removingUserUid);

        Account account = accountRepository.findOneByUid(accountUid);
        Group group = groupRepository.findOneByUid(groupUid);
        PaidGroup record = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group);
        User user = userRepository.findOneByUid(removingUserUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        record.setExpireDateTime(Instant.now());
        record.setRemovedByUser(user);
        account.removePaidGroup(record);
        group.setPaidFor(false);
        storeGroupAddOrRemoveLogs(AccountLogType.GROUP_REMOVED, account, group, record.getUid(), user);
    }

    @Override
    @Transactional
    public void sendFreeFormMessage(String userUid, String groupUid, String message) {
        // for now, just let the notification async handle the group loading etc., here just check the user
        // has permission (is account admin--later, account admin and it's a paid group, with enough credit

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Account account = user.getAccountAdministered();
        PaidGroup paidGroup = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group);

        authorizeFreeFormMessageSending(user, account, group, paidGroup);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        String description = group.getMembers().size() + " members @ : " + account.getFreeFormCost(); // so it's recorded at cost of sending

        AccountLog accountLog = new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.MESSAGE_SENT)
                .groupUid(groupUid)
                .paidGroupUid(paidGroup.getUid())
                .description(description)
                .build();

        bundle.addLog(accountLog);
        for (User member : group.getMembers()) {
            bundle.addNotification(new FreeFormMessageNotification(member, message, accountLog));
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    private void storeGroupAddOrRemoveLogs(AccountLogType accountLogType, Account account, Group group, String paidGroupUid, User user) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        bundle.addLog(new AccountLog.Builder(account)
                .userUid(user.getUid())
                .accountLogType(accountLogType)
                .groupUid(group.getUid())
                .paidGroupUid(paidGroupUid)
                .description(group.getName()).build());

        bundle.addLog(new GroupLog(group, user, accountLogType.equals(AccountLogType.GROUP_ADDED) ?
                GroupLogType.ADDED_TO_ACCOUNT : GroupLogType.GROUP_REMOVED, user.getId(), account.getUid()));
        logsAndNotificationsBroker.storeBundle(bundle);
    }

    private void authorizeFreeFormMessageSending(User user, Account account, Group group, PaidGroup paidGroup) {
        if (account == null || !account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if (!group.isPaidFor() || !paidGroup.isActive()) {
            throw new GroupNotPaidForException();
        }

        if (!paidGroup.getAccount().equals(account)) {
            throw new GroupAccountMismatchException();
        }
    }

}
