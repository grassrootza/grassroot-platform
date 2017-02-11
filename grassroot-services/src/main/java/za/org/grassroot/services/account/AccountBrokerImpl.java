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
import za.org.grassroot.core.enums.AccountBillingCycle;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountPaymentType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.Instant;
import java.util.*;

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

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final Environment environment;
    private final ApplicationEventPublisher eventPublisher;

    private final AccountGroupBroker accountGroupBroker;
    private final AccountBillingBroker accountBillingBroker;
    private final AccountSponsorshipBroker sponsorshipBroker;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, PermissionBroker permissionBroker,
                             LogsAndNotificationsBroker logsAndNotificationsBroker, AccountGroupBroker accountGroupBroker, AccountSponsorshipBroker sponsorshipBroker,
                             AccountBillingBroker accountBillingBroker, Environment environment, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.permissionBroker = permissionBroker;
        this.accountGroupBroker = accountGroupBroker;
        this.accountBillingBroker = accountBillingBroker;
        this.sponsorshipBroker = sponsorshipBroker;
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
        }

        log.info("Loaded account settings : messages = {}, group depth = {}", freeFormPerMonth, maxGroupNumber);
    }

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
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
                                AccountPaymentType accountPaymentType, AccountBillingCycle billingCycle) {
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

    private void setAccountLimits(Account account, AccountType accountType) {
        int subscriptionFee = (int) (accountFees.get(accountType) * (account.isAnnualAccount() ? annualDiscount : 1));
        account.setSubscriptionFee(subscriptionFee);
        account.setFreeFormMessages(freeFormPerMonth.get(accountType));
        account.setFreeFormCost(messagesCost.get(accountType));
        account.setMaxSizePerGroup(maxGroupSize.get(accountType));
        account.setMaxSubGroupDepth(maxSubGroupDepth.get(accountType));
        account.setMaxNumberGroups(maxGroupNumber.get(accountType));
        account.setTodosPerGroupPerMonth(todosPerMonth.get(accountType));
    }

    @Override
    @Transactional
    public void enableAccount(String userUid, String accountUid, String ongoingPaymentRef, boolean ensureUserAddedToAdmin, boolean setBillingUser) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user) && !sponsorshipBroker.hasUserBeenAskedToSponsor(userUid, accountUid)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setEnabled(true);
        account.setEnabledDateTime(Instant.now());
        account.setEnabledByUser(user);
        account.incrementBillingDate(AccountBillingBrokerImpl.STD_BILLING_HOUR, AccountBillingBrokerImpl.BILLING_TZ);

        if (!StringUtils.isEmpty(ongoingPaymentRef)) {
            account.setPaymentRef(ongoingPaymentRef);
        }

        if (setBillingUser && !account.getBillingUser().equals(user)) {
            account.setBillingUser(user);
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                .userUid(userUid)
                .description("account enabled")
                .build());

        // since the user that enables may be different to user that creates, and leaving out this role breaks a lot of UI, just make sure role is added (no regret)
        if (ensureUserAddedToAdmin && !account.equals(user.getPrimaryAccount())) {
            account.addAdministrator(user);
            user.addAccountAdministered(account);
            permissionBroker.addSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ADMIN_ADDED)
                    .userUid(userUid)
                    .description("account admin added during enabling")
                    .build());
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord, boolean removeAdminRole, boolean generateClosingBill) {
        User user = userRepository.findOneByUid(administratorUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (user.getPrimaryAccount() == null || !user.getPrimaryAccount().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if (generateClosingBill) {
            accountBillingBroker.generateClosingBill(administratorUid, accountUid);
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
                admin.setPrimaryAccount(null);
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
    public void closeAccount(String userUid, String accountUid, boolean generateClosingBill) {
        // note : this is to remove the account even from views (disabled accounts can be reenabled, so still show up)
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        Account account = accountRepository.findOneByUid(accountUid);
        if (account.isEnabled()) {
            account.setEnabled(false);
        }

        if (generateClosingBill) {
            accountBillingBroker.generateClosingBill(userUid, accountUid);
        }

        User user = userRepository.findOneByUid(userUid);
        if (!user.getPrimaryAccount().equals(account)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setVisible(false);
        for (User admin : account.getAdministrators()) {
            admin.setPrimaryAccount(null);
            permissionBroker.removeSystemRole(admin, BaseRoles.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ACCOUNT_INVISIBLE)
                .description("account closed and removed from view").build());
    }

    @Override
    @Transactional
    public void changeAccountType(String userUid, String accountUid, AccountType newAccountType, Set<String> groupsToRemove) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(newAccountType);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        int remainingGroups = (int) account.getPaidGroups().stream().filter(PaidGroup::isActive).count() -
                (groupsToRemove == null ? 0 : groupsToRemove.size());

        if (remainingGroups > maxGroupNumber.get(newAccountType)) {
            throw new AccountLimitExceededException();
        }

        if (groupsToRemove != null && !groupsToRemove.isEmpty()) {
            accountGroupBroker.removeGroupsFromAccount(accountUid, groupsToRemove, userUid);
        }

        accountBillingBroker.generateBillOutOfCycle(account.getUid(), false, false, null, false);

        account.setType(newAccountType);
        setAccountLimits(account, newAccountType);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.TYPE_CHANGED)
                .description(newAccountType.name()).build());
    }

    @Override
    @Transactional
    public void updateAccountGroupLimits(String userUid, String accountUid, int maxGroups, int maxSizePerGroup, int maxDepth, int messagesPerMonth, int todosPerMonth) {
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

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.DISCRETE_SETTING_CHANGE)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void updateAccountPaymentReference(String userUid, String accountUid, String paymentRef) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(paymentRef);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        account.setPaymentRef(paymentRef);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.PAYMENT_METHOD_CHANGED)
                .description(paymentRef).build());
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
        administrator.addAccountAdministered(account);
        permissionBroker.addSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);

        if (administrator.getPrimaryAccount() == null) {
            administrator.setPrimaryAccount(account);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description(administrator.getUid()).build());
    }

    @Override
    @Transactional
    public void removeAdministrator(String userUid, String accountUid, String adminToRemoveUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(adminToRemoveUid);

        if (userUid.equals(adminToRemoveUid)) {
            throw new AdminRemovalException("account.admin.remove.error.same");
        }

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(adminToRemoveUid);

        if (!account.getAdministrators().contains(changingUser)) {
            permissionBroker.validateSystemRole(changingUser, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if (administrator.equals(account.getBillingUser())) {
            throw new AdminRemovalException("account.admin.remove.error.bill");
        }

        account.removeAdministrator(administrator);
        administrator.removeAccountAdministered(account);
        if (administrator.getAccountsAdministered().isEmpty()) {
            administrator.setPrimaryAccount(null);
            permissionBroker.removeSystemRole(administrator, BaseRoles.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .userUid(userUid)
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
                .userUid(adminUid)
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
                .userUid(adminUid)
                .billedOrPaid(newFee)
                .description("Admin manually adjusted account fee").build());
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }
}