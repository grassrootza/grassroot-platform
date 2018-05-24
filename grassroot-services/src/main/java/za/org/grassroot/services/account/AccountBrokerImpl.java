package za.org.grassroot.services.account;

import org.apache.commons.collections4.map.HashedMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.AccountSpecifications.*;

/**
 * Created by luke on 2015/11/12.
 */
@Service
public class AccountBrokerImpl implements AccountBroker {

    private static final Logger log = LoggerFactory.getLogger(AccountBroker.class);

    private Map<AccountType, Integer> accountFees = new HashMap<>();
    private double annualDiscount;

    private Map<AccountType, Integer> freeFormPerMonth = new HashMap<>();
    private Map<AccountType, Integer> messagesCost = new HashMap<>();

    private Map<AccountType, Integer> maxGroupSize = new HashMap<>();
    private Map<AccountType, Integer> maxGroupNumber = new HashMap<>();
    private Map<AccountType, Integer> maxSubGroupDepth = new HashMap<>();

    private Map<AccountType, Integer> todosPerMonth = new HashMap<>();
    private Map<AccountType, Integer> eventsPerMonth = new HashedMap<>();

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final Environment environment;
    private final ApplicationEventPublisher eventPublisher;

    private final AccountGroupBroker accountGroupBroker;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, PermissionBroker permissionBroker,
                             LogsAndNotificationsBroker logsAndNotificationsBroker, AccountGroupBroker accountGroupBroker,
                             Environment environment, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.environment = environment;
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        annualDiscount = environment.getProperty("accounts.annual.discount", Double.class, (double) (10/12));
        for (AccountType accountType : AccountType.values()) {
            final String key = accountType.name().toLowerCase();
            accountFees.put(accountType, environment.getProperty("accounts.subscription.cost." + key, Integer.class));
            freeFormPerMonth.put(accountType, environment.getProperty("accounts.messages.monthly." + key, Integer.class));
            messagesCost.put(accountType, environment.getProperty("accounts.messages.msgcost." + key, Integer.class));
            maxGroupSize.put(accountType, environment.getProperty("accounts.group.limit." + key, Integer.class));
            maxGroupNumber.put(accountType, environment.getProperty("accounts.group.max." + key, Integer.class));
            maxSubGroupDepth.put(accountType, environment.getProperty("accounts.group.subdepth." + key, Integer.class));
            todosPerMonth.put(accountType, environment.getProperty("accounts.todos.monthly." + key, Integer.class));
            eventsPerMonth.put(accountType, environment.getProperty("accounts.events.monthly." + key, Integer.class));
        }

        log.info("Loaded account settings : messages = {}, group depth = {}", freeFormPerMonth, maxGroupNumber);
    }

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    private void validateAdmin(User user, Account account) {
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Account loadPrimaryAccountForUser(String userUid, boolean loadEvenIfDisabled) {
        User user = userRepository.findOneByUid(userUid);
        Account account = user.getPrimaryAccount();
        return (account != null && (loadEvenIfDisabled || account.isEnabled())) ? account : null;
    }

    @Override
    @Transactional
    public String createAccount(String userUid, String accountName, String billedUserUid, AccountType accountType,
                                AccountPaymentType accountPaymentType, AccountBillingCycle billingCycle, boolean enableFreeTrial) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(billedUserUid);
        Objects.requireNonNull(accountName);
        Objects.requireNonNull(accountType);

        User creatingUser = userRepository.findOneByUid(userUid);
        User billedUser = userRepository.findOneByUid(billedUserUid);

        Account account = new Account(creatingUser, accountName, accountType, billedUser, accountPaymentType,
                billingCycle == null ? AccountBillingCycle.MONTHLY : billingCycle);

        accountRepository.saveAndFlush(account);

        account.addAdministrator(billedUser);
        billedUser.setPrimaryAccount(account);
        permissionBroker.addSystemRole(billedUser, BaseRoles.ROLE_ACCOUNT_ADMIN);

        log.info("Created account, now looks like: " + account);

        setAccountLimits(account, accountType);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .user(creatingUser)
                .accountLogType(AccountLogType.ACCOUNT_CREATED)
                .description(accountType.name()).build());

        bundle.addLog(new AccountLog.Builder(account)
                .user(billedUser)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description("billed user set as admin").build());


        if (enableFreeTrial) {
            account.setEnabled(true);
            account.setEnabledDateTime(Instant.now());
            account.setEnabledByUser(creatingUser);
            account.setNextBillingDate(LocalDateTime.now().plusMonths(1).toInstant(ZoneOffset.UTC));
            account.setDefaultPaymentType(AccountPaymentType.FREE_TRIAL); // todo : rethink if want to handle it this way, and/or alter payment type selection UX/UI

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                    .user(creatingUser)
                    .description("account enabled for free trial, at creation")
                    .build());

            creatingUser.setHasUsedFreeTrial(true);
        }

        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);

        return account.getUid();
    }

    private void setAccountLimits(Account account, AccountType accountType) {
        account.setSubscriptionFee(calculateSubscriptionFee(account, accountType));
        account.setFreeFormMessages(freeFormPerMonth.get(accountType));
        account.setFreeFormCost(messagesCost.get(accountType));
        account.setMaxSizePerGroup(maxGroupSize.get(accountType));
        account.setMaxSubGroupDepth(maxSubGroupDepth.get(accountType));
        account.setMaxNumberGroups(maxGroupNumber.get(accountType));
        account.setTodosPerGroupPerMonth(todosPerMonth.get(accountType));
        account.setEventsPerGroupPerMonth(eventsPerMonth.get(accountType));
    }

    private int calculateSubscriptionFee(Account account, AccountType accountType) {
        return (int) (accountFees.get(accountType) * (account.isAnnualAccount() ? annualDiscount : 1));
    }

    @Override
    @Transactional
    public void enableAccount(String userUid, String accountUid, String ongoingPaymentRef, AccountPaymentType paymentType, boolean ensureUserAddedToAdmin, boolean setBillingUser) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setEnabled(true);
        account.setEnabledDateTime(Instant.now());
        account.setEnabledByUser(user);
        account.setDisabledDateTime(DateTimeUtil.getVeryLongAwayInstant());

        // make sure to store reg in here
        account.setDefaultPaymentType(paymentType);

        if (setBillingUser && !account.getBillingUser().equals(user)) {
            account.setBillingUser(user);
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                .user(user)
                .description("account enabled")
                .build());

        // since the user that enables may be different to user that creates, and leaving out this role breaks a lot of UI, just make sure role is added (no regret)
        if (ensureUserAddedToAdmin && !account.equals(user.getPrimaryAccount())) {
            account.addAdministrator(user);
            user.addAccountAdministered(account);
            permissionBroker.addSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ADMIN_ADDED)
                    .user(user)
                    .description("account admin added during enabling")
                    .build());
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void updateAccountPaymentCycleAndMethod(String userUid, String accountUid, AccountPaymentType paymentType, AccountBillingCycle billingCycle, boolean adjustNextBillingDate) {
        Objects.requireNonNull(accountUid);
        DebugUtil.transactionRequired("AccountBilling: ");

        // note : not validating user is admin as this may be called by a responding sponsor prior to payment being complete

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if (paymentType != null && !paymentType.equals(account.getDefaultPaymentType())) {
            account.setDefaultPaymentType(paymentType);
            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.PAYMENT_METHOD_CHANGED)
                    .description(paymentType.name()).build());
        }

        if (billingCycle != null && !billingCycle.equals(account.getBillingCycle())) {
            account.setBillingCycle(billingCycle);
            account.setSubscriptionFee(calculateSubscriptionFee(account, account.getType()));
            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.BILLING_CYCLE_CHANGED)
                    .description(billingCycle.name()).build());
        }

        logsAndNotificationsBroker.storeBundle(bundle);

    }

    @Override
    @Transactional
    public void updateAccountPaymentType(String userUid, String accountUid, AccountPaymentType paymentType) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(paymentType);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);
        validateAdmin(user, account);

        account.setDefaultPaymentType(paymentType);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.PAYMENT_METHOD_CHANGED)
                .user(user)
                .description("Changed to payment method: " + paymentType.name()).build());
    }

    @Override
    @Transactional
    public void setAccountPrimary(String userUid, String accountUid) {
        DebugUtil.transactionRequired("");
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            throw new IllegalArgumentException("Error! User must be an administrator of their primary account");
        }

        user.setPrimaryAccount(account);
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole, boolean generateClosingBill) {
        Objects.requireNonNull(administratorUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(administratorUid);
        Account account = accountRepository.findOneByUid(accountUid);
        validateAdmin(user, account);

        account.setEnabled(false);
        account.setDisabledDateTime(Instant.now());
        account.setDisabledByUser(user);

        for (Group paidGroup : account.getPaidGroups()) {
            paidGroup.setPaidFor(false);
        }

        if (removeAdminRole) {
            for (User admin : account.getAdministrators()) {
                admin.setPrimaryAccount(null);
                permissionBroker.removeSystemRole(admin, BaseRoles.ROLE_ACCOUNT_ADMIN);
            }
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(reasonToRecord).build());
    }

    @Override
    @Transactional
    public void closeAccount(String userUid, String accountUid, boolean generateClosingBill) {
        // note : this is to remove the account even from views (disabled accounts can be reenabled, so still show up)
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        if (account.isEnabled()) {
            account.setEnabled(false);
        }

        User user = userRepository.findOneByUid(userUid);
        validateAdmin(user, account); // note: this allows non-billing admin to close account, leaving for now but may revisit

        account.setVisible(false);

        log.info("removing {} paid groups", account.getPaidGroups().size());
        Set<String> paidGroupUids = account.getPaidGroups().stream().map(Group::getUid).collect(Collectors.toSet());
        accountGroupBroker.removeGroupsFromAccount(accountUid, paidGroupUids, userUid);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .description("account closed and removed from view").build());

        log.info("removing {} administrators", account.getAdministrators().size());
        account.getAdministrators().stream().filter(u -> !u.getUid().equals(userUid))
                .forEach(a -> removeAdministrator(userUid, accountUid, a.getUid(), false));

        removeAdministrator(userUid, accountUid, userUid, false); // at the end, remove self
    }

    @Override
    @Transactional
    public void changeAccountType(String userUid, String accountUid, AccountType newAccountType, Set<String> groupsToRemove) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(newAccountType);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        validateAdmin(user, account);

        int remainingGroups = (int) account.getPaidGroups().stream().filter(Group::isActive).count() -
                (groupsToRemove == null ? 0 : groupsToRemove.size());

        if (remainingGroups > maxGroupNumber.get(newAccountType)) {
            throw new AccountLimitExceededException();
        }

        if (groupsToRemove != null && !groupsToRemove.isEmpty()) {
            accountGroupBroker.removeGroupsFromAccount(accountUid, groupsToRemove, userUid);
        }

        account.setType(newAccountType);
        setAccountLimits(account, newAccountType);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.TYPE_CHANGED)
                .description(newAccountType.name()).build());
    }

    @Override
    @Transactional
    public void updateAccountGroupLimits(String userUid, String accountUid, int maxGroups, int maxSizePerGroup, int maxDepth, int messagesPerMonth, int todosPerMonth, int eventsPerMonth) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        StringBuilder sb = new StringBuilder("Account limits changed: ");

        if (account.getMaxNumberGroups() != maxGroups) {
            account.setMaxNumberGroups(maxGroups);
            sb.append("MaxGroups: ").append(maxGroups).append("; ");
        }

        if (account.getMaxSizePerGroup() != maxSizePerGroup) {
            account.setMaxSizePerGroup(maxSizePerGroup);
            sb.append("MaxSize: ").append(maxSizePerGroup).append("; ");
        }

        if (account.getMaxSubGroupDepth() != maxDepth) {
            account.setMaxSubGroupDepth(maxDepth);
            sb.append("Depth: ").append(maxDepth).append(";");
        }

        if (account.getFreeFormMessages() != messagesPerMonth) {
            account.setFreeFormMessages(messagesPerMonth);
            sb.append("Messages: ").append(messagesPerMonth).append(";");
        }

        if (account.getTodosPerGroupPerMonth() != todosPerMonth) {
            account.setTodosPerGroupPerMonth(todosPerMonth);
            sb.append("Todos per month: ").append(todosPerMonth).append(";");
        }

        if (account.getEventsPerGroupPerMonth() != eventsPerMonth) {
            account.setEventsPerGroupPerMonth(eventsPerMonth);
            sb.append("Events per month: ").append(eventsPerMonth).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
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

        validateAdmin(changingUser, account);

        account.addAdministrator(administrator);
        administrator.addAccountAdministered(account);
        permissionBroker.addSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);

        if (administrator.getPrimaryAccount() == null) {
            administrator.setPrimaryAccount(account);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(changingUser)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional
    public void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid, boolean preventRemovingSelfOrBilling) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(adminToRemoveUid);
        DebugUtil.transactionRequired("");

        if (preventRemovingSelfOrBilling && userUid.equals(adminToRemoveUid)) {
            throw new AdminRemovalException("account.admin.remove.error.same");
        }

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(adminToRemoveUid);

        validateAdmin(changingUser, account);

        if (preventRemovingSelfOrBilling && administrator.equals(account.getBillingUser())) {
            throw new AdminRemovalException("account.admin.remove.error.bill");
        }

        account.removeAdministrator(administrator);
        administrator.removeAccountAdministered(account);

        if (account.equals(administrator.getPrimaryAccount())) {
            administrator.setPrimaryAccount(null);
        }

        if (administrator.getAccountsAdministered().isEmpty()) {
            permissionBroker.removeSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(changingUser)
                .accountLogType(AccountLogType.ADMIN_REMOVED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional(readOnly = true)
    public List<Account> loadAllAccounts(boolean visibleOnly, AccountPaymentType paymentMethod, AccountBillingCycle billingCycle) {
        List<Account> accounts;
        boolean noSpecifications = !visibleOnly && paymentMethod == null && billingCycle == null;
        if (noSpecifications) {
            accounts = accountRepository.findAll();
        } else {
            Specifications<Account> specifications = null;
            if (visibleOnly) {
                specifications = where(isVisible());
            }
            if (paymentMethod != null) {
                specifications = (specifications == null) ? where(defaultPaymentType(paymentMethod)) :
                        specifications.and(defaultPaymentType(paymentMethod));
            }
            if (billingCycle != null) {
                specifications = (specifications == null) ? where(billingCycle(billingCycle)) :
                        specifications.and(billingCycle(billingCycle));
            }
            accounts = accountRepository.findAll(specifications);
        }
        accounts.sort(Comparator.comparing(Account::getAccountName));
        return accounts;
    }

    @Override
    public Map<AccountType, Integer> getNumberGroupsPerType() {
        return maxGroupNumber;
    }

    @Override
    public Map<AccountType, Integer> getNumberMessagesPerType() {
        return freeFormPerMonth;
    }

    @Override
    public Map<AccountType, Integer> getGroupSizeLimits() {
        return maxGroupSize;
    }

    @Override
    public Map<AccountType, Integer> getAccountTypeFees() {
        return accountFees;
    }

    @Override
    public Map<AccountType, Integer> getEventMonthlyLimits() {
        return eventsPerMonth;
    }

    @Override
    @Transactional
    public void resetAccountBillingDates(Instant commonInstant) {
        if (!environment.acceptsProfiles("production")) {
            accountRepository.findAll(where(isEnabled()))
                    .forEach(account -> account.setNextBillingDate(commonInstant));
        }
    }

    @Override
    @Transactional
    public void updateAccountBalance(String adminUid, String accountUid, long newBalance) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User admin = userRepository.findOneByUid(adminUid);

        permissionBroker.validateSystemRole(admin, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        account.setOutstandingBalance(newBalance);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.SYSADMIN_CHANGED_BALANCE)
                .user(admin)
                .billedOrPaid(newBalance)
                .description("Admin manually adjusted account balance").build());
    }

    @Override
    @Transactional
    public void updateAccountFee(String adminUid, String accountUid, long newFee) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User admin = userRepository.findOneByUid(adminUid);

        permissionBroker.validateSystemRole(admin, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        account.setSubscriptionFee((int) newFee);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.SYSADMIN_CHANGED_BALANCE)
                .user(admin)
                .billedOrPaid(newFee)
                .description("Admin manually adjusted account fee").build());
    }

    @Override
    @Transactional
    public void modifyAccount(String adminUid, String accountUid, AccountType accountType,
                              long subscriptionFee, boolean chargePerMessage, long costPerMessage) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(adminUid);
        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        StringBuilder sb = new StringBuilder("Changes: ");

        if (!account.getType().equals(accountType)) {
            account.setType(accountType);
            sb.append(" account type = ").append(accountType).append(";");
        }

        if (account.getSubscriptionFee() != (int) subscriptionFee) {
            account.setSubscriptionFee((int) subscriptionFee);
            sb.append(" subscription fee = ").append(subscriptionFee).append(";");
        }

        if (account.isBillPerMessage() != chargePerMessage) {
            account.setBillPerMessage(chargePerMessage);
            sb.append(" per message billing = ").append(chargePerMessage).append(";");
        }

        if (account.getFreeFormCost() != (int) costPerMessage) {
            account.setFreeFormMessages((int) costPerMessage);
            sb.append(" cost per message = ").append(costPerMessage).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.SYSADMIN_MODIFIED_MULTIPLE)
                .user(user)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void setAccountVisibility(String adminUid, String accountUid, boolean visible) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(adminUid);
        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        account.setEnabled(false);
        account.setVisible(false);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .user(user)
                .description("System admin set account invisible").build());
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void modifyAccount(String adminUid, String accountUid, AccountType accountType, String accountName, String billingEmail, AccountBillingCycle billingCycle) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(adminUid);
//        permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(accountUid);
        StringBuilder sb = new StringBuilder("Changes: ");

        if(!account.getType().equals(accountType)) {
            account.setType(accountType);
            sb.append(" account type = ").append(accountType).append(";");

            Map<AccountType, Integer> accountFees = getAccountTypeFees();
            Integer changedFee = accountFees.get(accountType);

            account.setSubscriptionFee(changedFee);
            sb.append(" subscription fee = ").append(changedFee).append(";");
        }

        if(!account.getAccountName().equals(accountName)) {
            account.setAccountName(accountName);
            sb.append(" account name = ").append(accountName).append(";");
        }

        if(!account.getBillingUser().getEmailAddress().equals(billingEmail)) {
            account.getBillingUser().setEmailAddress(billingEmail);
            sb.append(" account billing email = ").append(billingEmail).append(";");
        }

        if(!account.getBillingCycle().equals(billingCycle)) {
            account.setBillingCycle(billingCycle);
            sb.append(" account billing cycle = ").append(billingCycle).append(";");

        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.SYSADMIN_MODIFIED_MULTIPLE)
                .user(user)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void closeAccountRest(String userUid, String accountUid, boolean generateClosingBill) {
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(userUid));
        User user = userRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account); // note: this allows non-billing admin to close account, leaving for now but may revisit

        account.setEnabled(false);
        account.setVisible(false);

        log.info("removing {} paid groups", account.getPaidGroups().size());
        Set<String> paidGroupUids = account.getPaidGroups().stream().map(Group::getUid).collect(Collectors.toSet());
        accountGroupBroker.removeGroupsFromAccount(accountUid, paidGroupUids, userUid);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .description("account closed and removed from view").build());

        log.info("removing {} administrators", account.getAdministrators().size());
        account.getAdministrators().stream().filter(u -> !u.getUid().equals(userUid))
                .forEach(a -> removeAdministrator(userUid, accountUid, a.getUid(), false));

        removeAdministrator(userUid, accountUid, userUid, false); // at the end, remove self
    }
}