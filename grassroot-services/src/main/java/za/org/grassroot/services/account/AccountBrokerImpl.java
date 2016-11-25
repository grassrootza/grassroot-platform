package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.NotificationRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.specifications.AccountSpecifications;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static za.org.grassroot.services.specifications.NotificationSpecifications.*;

/**
 * Created by luke on 2015/11/12.
 */
@Service
public class AccountBrokerImpl implements AccountBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBroker.class);

    private Map<AccountType, Integer> accountFees = new HashMap<>();

    private Map<AccountType, Integer> freeFormPerMonth = new HashMap<>();
    private Map<AccountType, Integer> messagesCost = new HashMap<>();

    private Map<AccountType, Integer> maxGroupSize = new HashMap<>();
    private Map<AccountType, Integer> maxGroupNumber = new HashMap<>();
    private Map<AccountType, Integer> maxSubGroupDepth = new HashMap<>();

    private Map<AccountType, Integer> todosPerMonth = new HashMap<>();

    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private NotificationRepository notificationRepository;

    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private PermissionBroker permissionBroker;
    private Environment environment;
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, NotificationRepository notificationRepository,
                             PermissionBroker permissionBroker, LogsAndNotificationsBroker logsAndNotificationsBroker,
                             Environment environment, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.notificationRepository = notificationRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        for (AccountType accountType : AccountType.values()) {
            final String key = accountType.name().toLowerCase();
            accountFees.put(accountType, environment.getProperty("accounts.subscription.cost." + key, Integer.class));
            freeFormPerMonth.put(accountType, environment.getProperty("accounts.messages.monthly." + key, Integer.class));
            messagesCost.put(accountType, environment.getProperty("accounts.messages.msgcost." + key, Integer.class));
            maxGroupSize.put(accountType, environment.getProperty("accounts.group.limit." + key, Integer.class));
            maxGroupNumber.put(accountType, environment.getProperty("accounts.group.max." + key, Integer.class));
            maxSubGroupDepth.put(accountType, environment.getProperty("accounts.group.subdepth." + key, Integer.class));
            todosPerMonth.put(accountType, environment.getProperty("accounts.todos.monthly." + key, Integer.class));
        }

        log.info("Loaded account settings : messages = {}, group depth = {}", freeFormPerMonth, maxGroupNumber);
    }

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Account loadByPaymentRef(String paymentRef) {
        return accountRepository.findOneByPaymentRef(paymentRef);
    }

    @Override
    @Transactional(readOnly = true)
    public Account loadUsersAccount(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        Account account = user.getAccountAdministered();
        return (account != null && account.isEnabled()) ? account : null;
    }

    @Override
    @Transactional
    public String createAccount(String userUid, String accountName, String billedUserUid, AccountType accountType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(billedUserUid);
        Objects.requireNonNull(accountName);
        Objects.requireNonNull(accountType);

        User creatingUser = userRepository.findOneByUid(userUid);
        User billedUser = userRepository.findOneByUid(billedUserUid);

        Account account = new Account(creatingUser, accountName, accountType, billedUser);

        accountRepository.saveAndFlush(account);

        log.info("Created account, now looks like: " + account);

        account.setSubscriptionFee(accountFees.get(accountType));
        account.setFreeFormMessages(freeFormPerMonth.get(accountType));
        account.setFreeFormCost(messagesCost.get(accountType));
        account.setMaxSizePerGroup(maxGroupSize.get(accountType));
        account.setMaxSubGroupDepth(maxSubGroupDepth.get(accountType));
        account.setMaxNumberGroups(maxGroupNumber.get(accountType));
        account.setTodosPerGroupPerMonth(todosPerMonth.get(accountType));

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ACCOUNT_CREATED)
                .description(accountType.name()).build());

        bundle.addLog(new AccountLog.Builder(account)
                .userUid(billedUserUid)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description("billed user set as admin").build());

        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);

        return account.getUid();
    }

    @Override
    @Transactional
    public void enableAccount(String userUid, String accountUid, LocalDate nextStatementDate, String ongoingPaymentRef) {
        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        account.addAdministrator(user);
        user.setAccountAdministered(account);
        permissionBroker.addSystemRole(user, BaseRoles.ROLE_ACCOUNT_ADMIN);

        if (!user.getAccountAdministered().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setEnabled(true);
        account.setEnabledDateTime(Instant.now());
        account.setEnabledByUser(user);
        account.setNextBillingDate(nextStatementDate.atTime(AccountBillingBrokerImpl.STD_BILLING_HOUR)
                .toInstant(AccountBillingBrokerImpl.BILLING_TZ));

        if (!StringUtils.isEmpty(ongoingPaymentRef)) {
            account.setPaymentRef(ongoingPaymentRef);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                .userUid(userUid)
                .description("account enabled")
                .build());
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole) {
        User user = userRepository.findOneByUid(administratorUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!user.getAccountAdministered().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setEnabled(false);
        account.setDisabledDateTime(Instant.now());
        account.setDisabledByUser(user);

        for (PaidGroup paidGroup : account.getPaidGroups()) {
            paidGroup.setExpireDateTime(Instant.now());
            paidGroup.setRemovedByUser(user);
        }

        if (removeAdminRole) {
            for (User admin : account.getAdministrators()) {
                admin.setAccountAdministered(null);
                permissionBroker.removeSystemRole(admin, BaseRoles.ROLE_ACCOUNT_ADMIN);
            }
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(administratorUid)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(reasonToRecord).build());
    }

    @Override
    @Transactional
    public void makeAccountInvisible(String userUid, String accountUid) {
        // note : this is to remove the account even from views (disabled accounts can be reenabled, so still show up)
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        if (account.isEnabled()) {
            throw new IllegalArgumentException("Error! Only disabled accounts can be made invisible");
        }

        User user = userRepository.findOneByUid(userUid);
        if (!user.getAccountAdministered().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setVisible(false);
        for (User admin : account.getAdministrators()) {
            admin.setAccountAdministered(null);
            permissionBroker.removeSystemRole(admin, BaseRoles.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .description("account removed from view").build());
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

        /*
        todo : change to billing user design
        account.setPrimaryEmail(billingEmail);
        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.EMAIL_CHANGED)
                .description(billingEmail).build());
         */
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
        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.TYPE_CHANGED)
                .description(accountType.name()).build());
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

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.DISCRETE_SETTING_CHANGE)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void updateAccountMessageSettings(String userUid, String accountUid, int freeFormPerMonth, Integer costPerMessage) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        StringBuilder sb = new StringBuilder("Messaging settings changed: ");

        account.setFreeFormMessages(freeFormPerMonth);
        sb.append("FreeForm: ").append(freeFormPerMonth).append("; ");

        if (costPerMessage != null) {
            account.setFreeFormCost(costPerMessage);
            sb.append("CostPerMsg: ").append(costPerMessage).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.DISCRETE_SETTING_CHANGE)
                .description(sb.toString()).build());
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
        permissionBroker.addSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> loadAllAccounts(boolean visibleOnly) {
        return visibleOnly ?
                accountRepository.findAll(AccountSpecifications.isVisible()) : accountRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public int calculateMessagesLeftThisMonth(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);
        // NotificationSpecifications.

        long messagesThisMonth = notificationRepository.count(Specifications.where(
                accountLogTypeIs(AccountLogType.MESSAGE_SENT))
                .and(belongsToAccount(account))
                .and(createdTimeBetween(LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC), Instant.now())
        ));

        return Math.max(0, account.getFreeFormMessages() - (int) messagesThisMonth);
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.storeBundle(bundle);
    }
}