package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

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

    private AccountRepository accountRepository;
    private UserRepository userRepository;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private PermissionBroker permissionBroker;
    private Environment environment;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, PermissionBroker permissionBroker,
                             LogsAndNotificationsBroker logsAndNotificationsBroker, Environment environment) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.environment = environment;
    }

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
    @Transactional
    public String createAccount(String userUid, String accountName, String administratorUid, String billingEmail, AccountType accountType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountName);
        Objects.requireNonNull(accountType);

        User creatingUser = userRepository.findOneByUid(userUid);
        Account account = new Account(creatingUser, accountName, accountType, null);
        final String accountUid = account.getUid();

        accountRepository.saveAndFlush(account);

        /*
        todo : change to new design
        if (!StringUtils.isEmpty(billingEmail)) {
                account.setPrimaryEmail(billingEmail);
            }
         */

        if (!StringUtils.isEmpty(administratorUid)) {
            addAdministrator(userUid, accountUid, administratorUid);
        }

        account.setFreeFormMessages(messagesEnabled.get(accountType));
        account.setFreeFormCost(messagesCost.get(accountType));
        account.setMaxSizePerGroup(maxGroupSize.get(accountType));
        account.setMaxSubGroupDepth(maxSubGroupDepth.get(accountType));
        account.setMaxNumberGroups(maxGroupNumber.get(accountType));

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ACCOUNT_CREATED)
                .description(accountType.name()).build());

        return account.getUid();
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord) {
        User user = userRepository.findOneByUid(administratorUid);
        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        account.setDisabledDateTime(Instant.now());
        account.setDisabledByUser(user);
        account.setDisabledDateTime(Instant.now());

        for (PaidGroup paidGroup : account.getPaidGroups()) {
            paidGroup.setExpireDateTime(Instant.now());
            paidGroup.setRemovedByUser(user);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(administratorUid)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(reasonToRecord).build());
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

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.DISCRETE_SETTING_CHANGE)
                .description(sb.toString()).build());
    }

    @Override
    public Map<AccountType, Long> fetchAccountTypesAndFees() {
        return null;
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
    public List<Account> loadAllAccounts() {
        return accountRepository.findAll();
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.storeBundle(bundle);
    }
}