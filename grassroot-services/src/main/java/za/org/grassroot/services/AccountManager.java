package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.PaidGroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2015/11/12.
 * todo: decide where to implement permission checking for all of this (front end / services, where in services)
 * todo: equals logic in account to prevent duplication
 */
@Service
@Transactional
public class AccountManager implements AccountManagementService {

    private static final Logger log = LoggerFactory.getLogger(AccountManager.class);

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private PaidGroupRepository paidGroupRepository;

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private RoleManagementService roleManagementService;

    @Autowired
   	private UserRepository userRepository;

   	@Autowired
   	private GroupRepository groupRepository;

   	@Autowired
   	private LogsAndNotificationsBroker logsAndNotificationsBroker;


    @Override
    public Account createAccount(String accountName) {
        log.info("Okay, creating a bare bones account ... with name: " + accountName);
        Account newAccount = new Account(accountName, true);
        return accountRepository.save(newAccount);
    }

    @Override
    public Account createAccount(String accountName, User administrator) {
        Account account = accountRepository.save(new Account(accountName, administrator));
        log.info("Created the account ... " + account.toString());
        addAdminToUser(administrator, account);
        return accountRepository.save(account);
    }

    @Override
    public Account createAccount(String accountName, User administrator, String billingEmail, boolean enabled) {
        Account account = accountRepository.save(new Account(accountName, administrator, billingEmail, enabled));
        addAdminToUser(administrator, account);
        return account;
    }

    @Override
    public Account addAdministrator(Account account, User administrator) {
        account.addAdministrator(administrator);
        addAdminToUser(administrator, account);
        return accountRepository.save(account);
    }

    @Override
    public Account setBillingEmail(Account account, String billingEmail) {
        account.setPrimaryEmail(billingEmail);
        return accountRepository.save(account);
    }

    @Override
    public Account adjustSettings(Account changedAccount) {
        Account savedAccount = accountRepository.findOne(changedAccount.getId());
        savedAccount.setFreeFormMessages(changedAccount.isFreeFormMessages());
        savedAccount.setRelayableMessages(changedAccount.isRelayableMessages());
        savedAccount.setLogbookExtraMessages(changedAccount.isLogbookExtraMessages());
        return accountRepository.save(savedAccount);
    }

    /*
    Helper function to make sure second side of relationship & admin role added to user
     */
    private User addAdminToUser(User user, Account account) {
        // todo: check if there are redundant calls here (though this won't be used often)
        log.info("Wiring up user to account admin ... ");
        user.setAccountAdministered(account);
        User savedUser = userManagementService.save(user);
        log.info("User account admin set ... User: " + savedUser.toString());
        return roleManagementService.addStandardRoleToUser(BaseRoles.ROLE_ACCOUNT_ADMIN, savedUser);
    }

    @Override
    public Account loadAccount(Long accountId) {
        return accountRepository.findOne(accountId);
    }

    @Override
    public Account findAccountByAdministrator(User administrator) {
        return accountRepository.findByAdministrators(administrator);
    }

    @Override
    public List<Account> loadAllAccounts() {
        return accountRepository.findAll();
    }

    @Override
    @Transactional
    public Group addGroupToAccount(Account account, Group group, User addingUser) throws GroupAlreadyPaidForException {
        // todo: check it isn't already added, didn't exist before, etc

        // do this a bit more elegantly
        if (group.isPaidFor())
            throw new GroupAlreadyPaidForException();
        PaidGroup paidGroup = paidGroupRepository.save(new PaidGroup(group, account, addingUser));
        account.addPaidGroup(paidGroup);
        group.setPaidFor(true);
        return group;
        // return groupManagementService.saveGroup(group, true, "Set paid for", addingUser.getId());
    }

    @Override
    public Account findAccountForGroup(Group group) {
        if (!group.isPaidFor())
            return null;
        else
            return paidGroupRepository.findByGroupOrderByExpireDateTimeDesc(group).get(0).getAccount();
    }

    @Override
    @Transactional
    public Account removeGroupFromAccount(Account account, PaidGroup paidGroupRecord, User removingUser) {
        Group group = paidGroupRecord.getGroup();

        paidGroupRecord.setExpireDateTime(Instant.now());
        paidGroupRecord.setRemovedByUser(removingUser);

        account.removePaidGroup(paidGroupRecord);
        group.setPaidFor(false);

        paidGroupRepository.save(paidGroupRecord);
        log.info("PaidGroup entity now ... " + paidGroupRecord);
        // groupManagementService.saveGroup(group, true,"Remove paid for",removingUser.getId());
        return accountRepository.save(account);
    }

    @Override
    public List<PaidGroup> getGroupsPaidForByAccount(Account account) {
        // todo: remove this and just use getGroupsPaidFor
        return paidGroupRepository.findByAccount(account);
    }

    @Override
    public PaidGroup loadPaidGroupEntity(Long paidGroupId) {
        return paidGroupRepository.findOne(paidGroupId);
    }

    @Override
   	@org.springframework.transaction.annotation.Transactional
   	public void sendFreeFormMessage(String userUid, String groupUid, String message) {
   		// todo: move most of this to AccountManager
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
