package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2015/11/12.
 */
@Service
public class AccountManager implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
   	private UserRepository userRepository;

   	@Autowired
   	private GroupRepository groupRepository;

    @Autowired
    private RoleRepository roleRepository;

   	@Autowired
   	private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    @Override
    @Transactional(readOnly = true)
    public PaidGroup loadPaidGroup(String paidGroupUid) {
        return paidGroupRepository.findOneByUid(paidGroupUid);
    }

    @Override
    @Transactional
    public String createAccount(String userUid, String accountName, String administratorUid, String billingEmail) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountName);

        User creatingUser = userRepository.findOneByUid(userUid);
        Account account = new Account(creatingUser, accountName);
        final String accountUid = account.getUid();

        if (!StringUtils.isEmpty(billingEmail)) {
            account.setPrimaryEmail(billingEmail);
        }

        /*if (!StringUtils.isEmpty(administratorUid)) {
            AfterTxCommitTask afterTxCommitTask = () -> addAdministrator(userUid, accountUid, administratorUid);
            applicationEventPublisher.publishEvent(afterTxCommitTask);
        }*/

        accountRepository.saveAndFlush(account);

        addAdministrator(userUid, accountUid, administratorUid);

        return account.getUid();
    }

    @Override
    @Transactional
    public void updateBillingEmail(String userUid, String accountUid, String billingEmail) {
        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        // todo : system admin check & logging

        account.setPrimaryEmail(billingEmail);
    }

    @Override
    @Transactional
    public void updateSettings(Account changedAccount) {
        Account savedAccount = accountRepository.findOne(changedAccount.getId());
        savedAccount.setFreeFormMessages(changedAccount.isFreeFormMessages());
        savedAccount.setRelayableMessages(changedAccount.isRelayableMessages());
        savedAccount.setTodoExtraMessages(changedAccount.isTodoExtraMessages());
    }

    @Override
    @Transactional
    public void addAdministrator(String userUid, String accountUid, String administratorUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(administratorUid);

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(administratorUid);

        // todo : permission check & log creation

        account.addAdministrator(administrator);
        administrator.setAccountAdministered(account);
        Role adminRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_ACCOUNT_ADMIN, Role.RoleType.STANDARD).get(0);
        administrator.addStandardRole(adminRole);
    }

    @Override
    public List<Account> loadAllAccounts() {
        return accountRepository.findAll();
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

        // todo: check permissions etc

        // do this a bit more elegantly
        if (group.isPaidFor()) {
            throw new GroupAlreadyPaidForException();
        }

        PaidGroup paidGroup = new PaidGroup(group, account, addingUser);

        paidGroupRepository.saveAndFlush(paidGroup);

        account.addPaidGroup(paidGroup);
        group.setPaidFor(true);
    }

    @Override
    @Transactional(readOnly =  true)
    public Account findAccountForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        if (!group.isPaidFor())
            return null;
        else
            return paidGroupRepository.findByGroupOrderByExpireDateTimeDesc(group).get(0).getAccount();
    }

    @Override
    @Transactional
    public void removeGroupFromAccount(String accountUid, String paidGroupUid, String removingUserUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(paidGroupUid);
        Objects.requireNonNull(removingUserUid);

        // todo : permissions & logs

        Account account = accountRepository.findOneByUid(accountUid);
        PaidGroup record = paidGroupRepository.findOneByUid(paidGroupUid);
        Group group = record.getGroup();
        User user = userRepository.findOneByUid(removingUserUid);

        record.setExpireDateTime(Instant.now());
        record.setRemovedByUser(user);
        account.removePaidGroup(record);
        group.setPaidFor(false);

    }

    @Override
   	@Transactional
   	public void sendFreeFormMessage(String userUid, String groupUid, String message) {
   		// for now, just let the notification async handle the group loading etc., here just check the user
   		// has permission (is account admin--later, account admin and it's a paid group, with enough credit

   		User user = userRepository.findOneByUid(userUid);
   		Group group = groupRepository.findOneByUid(groupUid);
   		Account account = user.getAccountAdministered();

   		authorizeFreeFormMessageSending(user, account);

   		LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

   		AccountLog accountLog = new AccountLog(userUid, account, AccountLogType.MESSAGE_SENT, groupUid, null,
   				"Sent free form message: " + message);
   		bundle.addLog(accountLog);

   		for (User member : group.getMembers()) {
   			bundle.addNotification(new FreeFormMessageNotification(member, message, accountLog));
   		}

   		logsAndNotificationsBroker.storeBundle(bundle);
   	}

   	private void authorizeFreeFormMessageSending(User user, Account account) {
   		Set<String> standardRoleNames = user.getStandardRoles().stream().map(Role::getName).collect(Collectors.toSet());
   		if (account == null || !standardRoleNames.contains(BaseRoles.ROLE_ACCOUNT_ADMIN)) {
   			throw new AccessDeniedException("User not account admin!");
   		}
   	}
}
