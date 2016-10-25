package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.exception.GroupAccountMismatchException;
import za.org.grassroot.services.exception.GroupAlreadyPaidForException;
import za.org.grassroot.services.exception.GroupNotPaidForException;
import za.org.grassroot.services.specifications.AccountSpecifications;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;

import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2015/11/12.
 */
@Service
public class AccountBrokerImpl implements AccountBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBroker.class);

    private Map<AccountType, Boolean> messagesEnabled = new HashMap<>();
    private Map<AccountType, Integer> messagesCost = new HashMap<>();

    private Map<AccountType, Integer> maxGroupSize = new HashMap<>();
    private Map<AccountType, Integer> maxGroupNumber = new HashMap<>();
    private Map<AccountType, Integer> maxSubGroupDepth = new HashMap<>();

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
    private NotificationRepository notificationRepository;

   	@Autowired
   	private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    private PermissionBroker permissionBroker;

    @Autowired
    private Environment environment;

    @PostConstruct
    public void init() {
        for (AccountType accountType : AccountType.values()) {
            final String key = accountType.name().toLowerCase();
            messagesEnabled.put(accountType, environment.getProperty("accounts.messages.enabled." + key, Boolean.class));
            messagesCost.put(accountType, environment.getProperty("accounts.messages.msgcost." + key, Integer.class));
            maxGroupSize.put(accountType, environment.getProperty("accounts.group.limit." + key, Integer.class));
            maxGroupNumber.put(accountType, environment.getProperty("accounts.group.max." + key, Integer.class));
            maxSubGroupDepth.put(accountType, environment.getProperty("accounts.group.subdepth." + key, Integer.class));
        }

        log.info("Loaded account settings : messages = {}, group depth = {}", messagesEnabled, maxGroupNumber);
    }

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
    public String createAccount(String userUid, String accountName, String administratorUid, String billingEmail, AccountType accountType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountName);
        Objects.requireNonNull(accountType);

        User creatingUser = userRepository.findOneByUid(userUid);
        Account account = new Account(creatingUser, accountName, accountType);
        final String accountUid = account.getUid();

        if (!StringUtils.isEmpty(billingEmail)) {
            account.setPrimaryEmail(billingEmail);
        }

        accountRepository.saveAndFlush(account);

        if (!StringUtils.isEmpty(administratorUid)) {
            addAdministrator(userUid, accountUid, administratorUid);
        }

        account.setFreeFormMessages(messagesEnabled.get(accountType));
        account.setFreeFormCost(messagesCost.get(accountType));
        account.setMaxSizePerGroup(maxGroupSize.get(accountType));
        account.setMaxSubGroupDepth(maxSubGroupDepth.get(accountType));
        account.setMaxNumberGroups(maxGroupNumber.get(accountType));

        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.ACCOUNT_CREATED, accountType.name()));

        return account.getUid();
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord) {
        User user = userRepository.findOneByUid(administratorUid);
        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        account.setEnabled(false);
        account.setDisabledByUser(user);
        account.setDisabledDateTime(Instant.now());

        for (PaidGroup paidGroup : account.getPaidGroups()) {
            paidGroup.setExpireDateTime(Instant.now());
            paidGroup.setRemovedByUser(user);
        }

        createAndStoreSingleAccountLog(new AccountLog(administratorUid, account, AccountLogType.ACCOUNT_DISABLED, reasonToRecord));
    }

    @Override
    @Transactional
    public void updateBillingEmail(String userUid, String accountUid, String billingEmail) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        log.info("Looking up administrators for this account: {}, from this uid: {}", account, accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setPrimaryEmail(billingEmail);
        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.EMAIL_CHANGED, billingEmail));
    }

    @Override
    @Transactional
    public void changeAccountType(String userUid, String accountUid, AccountType accountType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(accountType);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setType(accountType);
        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.TYPE_CHANGED, accountType.name()));
    }

    @Override
    @Transactional
    public void updateAccountGroupLimits(String userUid, String accountUid, Integer maxGroups, Integer maxSizePerGroup, Integer maxDepth) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        StringBuilder sb = new StringBuilder("Group settings changed: ");

        if (maxGroups != null) {
            account.setMaxNumberGroups(maxGroups);
            sb.append("MaxGroups: ").append(maxGroups).append("; ");
        }

        if (maxSizePerGroup != null) {
            account.setMaxSizePerGroup(maxSizePerGroup);
            sb.append("MaxSize: ").append(maxSizePerGroup).append("; ");
        }

        if (maxDepth != null) {
            account.setMaxSubGroupDepth(maxDepth);
            sb.append("Depth: ").append(maxDepth).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.DISCRETE_SETTING_CHANGE, sb.toString()));
    }

    @Override
    @Transactional
    public void updateAccountMessageSettings(String userUid, String accountUid, Boolean freeFormEnabled, Integer costPerMessage) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        StringBuilder sb = new StringBuilder("Messaging settings changed: ");

        if (freeFormEnabled != null) {
            account.setFreeFormMessages(freeFormEnabled);
            sb.append("FreeForm: ").append(freeFormEnabled).append("; ");
        }

        if (costPerMessage != null) {
            account.setFreeFormCost(costPerMessage);
            sb.append("CostPerMsg: ").append(costPerMessage).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.DISCRETE_SETTING_CHANGE, sb.toString()));
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

        if (!account.getAdministrators().contains(changingUser)) {
            permissionBroker.validateSystemRole(changingUser, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.addAdministrator(administrator);
        administrator.setAccountAdministered(account);
        Role adminRole = roleRepository.findByNameAndRoleType(BaseRoles.ROLE_ACCOUNT_ADMIN, Role.RoleType.STANDARD).get(0);
        administrator.addStandardRole(adminRole);

        createAndStoreSingleAccountLog(new AccountLog(userUid, account, AccountLogType.ADMIN_ADDED, administrator.getUid()));
    }

    @Override
    @Transactional(readOnly = true)
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

   		AccountLog accountLog = new AccountLog(userUid, account, AccountLogType.MESSAGE_SENT, groupUid, paidGroup.getUid(), description);

        bundle.addLog(accountLog);
   		for (User member : group.getMembers()) {
   			bundle.addNotification(new FreeFormMessageNotification(member, message, accountLog));
   		}

   		logsAndNotificationsBroker.storeBundle(bundle);
   	}

    @Override
    public Map<Account, Long> calculateMonthlyStatements(Instant startValidity, Instant endValidity) {

        List<Account> validAccounts = accountRepository.findAll(AccountSpecifications.isValidBetween(startValidity, endValidity));
        log.info("Calculating monthly statements for {} accounts", validAccounts.size());
        Map<Account, Long> returnMap = new HashMap<>();

        for (Account account : validAccounts) {
            // todo : fix this calculate method (also, we will need billing)
            returnMap.put(account, calculateAccountCostsInPeriod(account.getUid(), startValidity, endValidity, false));
        }

        return returnMap;
    }

    @Override
    @Transactional
    public long calculateAccountCostsInPeriod(String accountUid, Instant periodStart, Instant periodEnd, boolean generateLog) {
        Account account = accountRepository.findOneByUid(accountUid);

        if (account.getDisabledDateTime().isBefore(periodStart)) {
            return 0;
        }

        // todo : watch Hibernate on this for excessive DB calls (though this is, for the moment, an infrequent batch call)
        Set<PaidGroup> paidGroups = account.getPaidGroups();
        final int messageCost = account.getFreeFormCost(); // todo : make time based? else will mess up retrospective ...

        Specifications<Notification> notificationCounter = Specifications.where(wasDelivered())
                .and(createdTimeBetween(periodStart, periodEnd))
                .and(belongsToAccount(account));

        long costAccumulator = notificationRepository.count(notificationCounter) * messageCost;

        for (PaidGroup paidGroup : paidGroups) {
            costAccumulator += (countMessagesForPaidGroup(paidGroup, periodStart, periodEnd) * messageCost);
        }

        return costAccumulator;
    }

    private long countMessagesForPaidGroup(PaidGroup paidGroup, Instant periodStart, Instant periodEnd) {
        Group group = paidGroup.getGroup();
        Instant start = paidGroup.getActiveDateTime().isBefore(periodStart) ? periodStart : paidGroup.getActiveDateTime();
        Instant end = paidGroup.getExpireDateTime() == null || paidGroup.getExpireDateTime().isAfter(periodEnd) ?
                periodEnd : paidGroup.getExpireDateTime();

        Specifications<Notification> specifications = Specifications.where(wasDelivered())
                .and(createdTimeBetween(start, end))
                .and(ancestorGroupIs(group));

        return notificationRepository.count(specifications);
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

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.storeBundle(bundle);
    }

    private void storeGroupAddOrRemoveLogs(AccountLogType accountLogType, Account account, Group group, String paidGroupUid, User user) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog(user.getUid(), account, accountLogType, group.getUid(), paidGroupUid, group.getName()));
        bundle.addLog(new GroupLog(group, user, accountLogType.equals(AccountLogType.GROUP_ADDED) ?
                GroupLogType.ADDED_TO_ACCOUNT : GroupLogType.GROUP_REMOVED, user.getId(), account.getUid()));
        logsAndNotificationsBroker.storeBundle(bundle);
    }
}