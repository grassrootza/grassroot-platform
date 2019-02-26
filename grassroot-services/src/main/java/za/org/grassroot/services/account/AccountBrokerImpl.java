package za.org.grassroot.services.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.Account_;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.dto.group.GroupRefDTO;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.AccountType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.AccountRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.integration.location.LocationInfoBroker;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AdminRemovalException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.persistence.EntityManager;
import javax.persistence.TypedQuery;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.TemporalAdjusters;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2015/11/12.
 */
@Service @Slf4j
public class AccountBrokerImpl implements AccountBroker {

    @Value("${accounts.freeform.cost.standard:22}")
    private int additionalMessageCost;

    private final AccountRepository accountRepository;
    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final MembershipRepository membershipRepository;

    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final PermissionBroker permissionBroker;
    private final ApplicationEventPublisher eventPublisher;

    private EntityManager entityManager;
    private LocationInfoBroker locationInfoBroker;

    @Autowired
    public AccountBrokerImpl(AccountRepository accountRepository, UserRepository userRepository, GroupRepository groupRepository, MembershipRepository membershipRepository, PermissionBroker permissionBroker,
                             LogsAndNotificationsBroker logsAndNotificationsBroker, ApplicationEventPublisher eventPublisher) {
        this.accountRepository = accountRepository;
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.permissionBroker = permissionBroker;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.eventPublisher = eventPublisher;
    }

    @Autowired
    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    @Autowired(required = false)
    public void setLocationInfoBroker(LocationInfoBroker locationInfoBroker) {
        this.locationInfoBroker = locationInfoBroker;
    }

    @Override
    public Account loadAccount(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    @Override
    public Account loadDefaultAccountForUser(String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        if (user.getPrimaryAccount() != null)
            return user.getPrimaryAccount();

        // might have a situation where user has had primary account closed, but still admin on others, in which case,
        // make a random guess, using most recent non-closed account

        return accountRepository.findTopByAdministratorsAndClosedFalse(user, new Sort(Sort.Direction.DESC, "createdDateTime"));
    }

    @Override
    @Transactional(readOnly = true)
    public void validateUserCanViewAccount(String accountUid, String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));
        validateAdmin(user, account);
    }

    private void validateAdmin(User user, Account account) {
        Objects.requireNonNull(user);
        Objects.requireNonNull(account);

        if (account.getAdministrators() == null || !account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);
        }
    }

    @Override
    @Transactional
    public String createAccount(String userUid, String accountName, String billedUserUid, String billingEmail, String ongoingPaymentRef) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(billedUserUid);
        Objects.requireNonNull(accountName);

        User creatingUser = userRepository.findOneByUid(userUid);
        User billedUser = userRepository.findOneByUid(billedUserUid);

        Account account = new Account(creatingUser, accountName, AccountType.STANDARD, billedUser);

        accountRepository.saveAndFlush(account);

        account.addAdministrator(billedUser);
        billedUser.setPrimaryAccount(account);

        if (!StringUtils.isEmpty(billingEmail))
            account.setPrimaryBillingEmail(billingEmail);
        else
            account.setPrimaryBillingEmail(billedUser.getEmailAddress());

        permissionBroker.addSystemRole(billedUser, StandardRole.ROLE_ACCOUNT_ADMIN);

        log.info("Created account, now looks like: " + account);

        account.setFreeFormCost(additionalMessageCost);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .user(creatingUser)
                .accountLogType(AccountLogType.ACCOUNT_CREATED)
                .description(accountName + ":" + billedUserUid).build());

        bundle.addLog(new AccountLog.Builder(account)
                .user(billedUser)
                .accountLogType(AccountLogType.ADMIN_ADDED)
                .description("billed user set as admin").build());

        if (!StringUtils.isEmpty(ongoingPaymentRef)) {
            account.setEnabled(true);
            account.setEnabledByUser(creatingUser);
            account.setPaymentRef(ongoingPaymentRef);

            bundle.addLog(new AccountLog.Builder(account)
                    .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                    .user(creatingUser)
                    .description("account enabled for free trial, at creation")
                    .build());

        }

        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
        eventPublisher.publishEvent(afterTxCommitTask);

        return account.getUid();
    }

    @Override
    @Transactional
    public void setAccountSubscriptionRef(String userUid, String accountUid, String subscriptionId) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account);

        account.setSubscriptionRef(subscriptionId);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .accountLogType(AccountLogType.ACCOUNT_SUB_ID_CHANGED)
                .description(subscriptionId).build());
    }

    @Override
    public void setAccountPaymentRef(String userUid, String accountUid, String paymentRef) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        validateAdmin(user, account);

        account.setPaymentRef(paymentRef);

        if (!account.isEnabled())
            account.setEnabled(true);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .accountLogType(AccountLogType.PAYMENT_CHANGED)
                .description(paymentRef).build());
    }

    @Override
    @Transactional
    public void setLastBillingDate(String userUid, String accountUid, Instant newLastBillingDate) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);

        Instant priorDate = account.getLastBillingDate();
        account.setLastBillingDate(newLastBillingDate);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .description("from: " + priorDate + "; to: " + newLastBillingDate)
                .accountLogType(AccountLogType.LAST_BILLING_DATE_CHANGED).build());
    }

    @Override
    @Transactional
    public void enableAccount(String userUid, String accountUid, String logMessage) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);

        account.setEnabled(true);
        account.setEnabledByUser(user);
        account.setDisabledDateTime(DateTimeUtil.getVeryLongAwayInstant());

        for (Group paidGroup : account.getPaidGroups()) {
            paidGroup.setPaidFor(true);
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.ACCOUNT_ENABLED)
                .user(user)
                .description(logMessage)
                .build());

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
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
    public void disableAccount(String administratorUid, String accountUid, String reasonToRecord) {
        Objects.requireNonNull(administratorUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(administratorUid);
        Account account = accountRepository.findOneByUid(accountUid);
        validateAdmin(user, account);

        account.setEnabled(false);
        account.setDisabledDateTime(Instant.now());
        account.setDisabledByUser(user);

        // note: don't remove admins & groups because they should be preserved if the account is enabled again
        for (Group paidGroup : account.getPaidGroups()) {
            paidGroup.setPaidFor(false);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(reasonToRecord).build());
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
        permissionBroker.addSystemRole(administrator, StandardRole.ROLE_ACCOUNT_ADMIN);

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

        log.info("removing admin, user: {}, admin to remove uid: {}, account uid: {}", userUid, adminToRemoveUid, accountUid);

        if (preventRemovingSelfOrBilling && userUid.equals(adminToRemoveUid)) {
            throw new AdminRemovalException("account.admin.remove.error.same");
        }

        User changingUser = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        User administrator = userRepository.findOneByUid(adminToRemoveUid);

        validateAdmin(changingUser, account);

        account.removeAdministrator(administrator);
        administrator.removeAccountAdministered(account);

        if (account.equals(administrator.getPrimaryAccount())) {
            administrator.setPrimaryAccount(null);
        }

        if (administrator.getAccountsAdministered() == null || administrator.getAccountsAdministered().isEmpty()) {
            permissionBroker.removeSystemRole(administrator, StandardRole.ROLE_ACCOUNT_ADMIN);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(changingUser)
                .accountLogType(AccountLogType.ADMIN_REMOVED)
                .description(administrator.getUid()).build());
    }

    @Async
    @Override
    @Transactional
    public void addGroupsToAccount(String accountUid, Set<String> groupUids, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUids);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);
        validateAdmin(user, account);

        List<Group> groups = groupRepository.findAll(Specification.where(GroupSpecifications.uidIn(groupUids)));
        log.info("number of groups matching list: {}", groups.size());

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        groups.stream()
                .filter(group -> !group.isPaidFor())
                .forEach(group -> {
                    group.setAccount(account);
                    group.setPaidFor(true);

                    log.info("Added group {} to account {}", group.getName(), account.getName());
                    groupRepository.saveAndFlush(group);

                    bundle.addLog(new AccountLog.Builder(account)
                            .user(user)
                            .accountLogType(AccountLogType.GROUP_ADDED)
                            .group(group)
                            .paidGroupUid(group.getUid())
                            .description(group.getName()).build());

                    bundle.addLog(new GroupLog(group, user, GroupLogType.ADDED_TO_ACCOUNT,
                            null, null, account, "Group added to Grassroot Extra accounts"));
        });

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void addAllUserCreatedGroupsToAccount(String accountUid, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        validateAdmin(user, account);

        Set<String> groups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user)
                .stream().map(Group::getUid).collect(Collectors.toSet());

        addGroupsToAccount(accountUid, groups, userUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Set<Group> fetchGroupsUserCanAddToAccount(String accountUid, String userUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));
        validateAdmin(user, account); // should never need to call this witohut admin

        // expensive, but will be _very_ occassional
        Set<Group> userGroups = permissionBroker.getActiveGroupsWithPermission(user, null);
        log.debug("User has {} groups that they belong to", userGroups.size());
        Set<Group> userGroupsUnpaidFor = userGroups.stream().filter(group -> !group.isPaidFor()).collect(Collectors.toSet());
        log.debug("After removing paid groups, {} left", userGroupsUnpaidFor.size());

        return userGroupsUnpaidFor;
    }

    @Override
    @Transactional
    public void removeGroupsFromAccount(String accountUid, Set<String> groupUids, String removingUserUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUids);
        Objects.requireNonNull(removingUserUid);

        Account account = accountRepository.findOneByUid(accountUid);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (String groupUid : groupUids) {
            Group group = groupRepository.findOneByUid(groupUid);
            User user = userRepository.findOneByUid(removingUserUid);

            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);
            }

            account.removePaidGroup(group);

            // have to do double side else unpredictable
            group.setPaidFor(false);
            group.setAccount(null);

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_REMOVED)
                    .group(group)
                    .paidGroupUid(group.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group,
                    user,
                    GroupLogType.REMOVED_FROM_ACCOUNT,
                    null, null, account, "Group removed from Grassroot Extra"));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public GroupRefDTO fetchGroupAccountInfo(String userUid, String groupUid) {
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        Group group = groupRepository.findOneByUid(Objects.requireNonNull(groupUid));

        if (group.getAccount() == null || !user.getAccountsAdministered().contains(group.getAccount()))
            throw new IllegalArgumentException("Error! Group is not on user's account");

        return new GroupRefDTO(group, this.membershipRepository);
    }

    @Override
    @Transactional
    public void renameAccount(String adminUid, String accountUid, String accountName) {
        Objects.requireNonNull(adminUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(adminUid);
        Account account = accountRepository.findOneByUid(accountUid);

        validateAdmin(user, account);

        StringBuilder sb = new StringBuilder("Changes: ");

        if(!account.getAccountName().equals(accountName)) {
            account.setAccountName(accountName);
            sb.append(" account name = ").append(accountName).append(";");
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.NAME_CHANGED)
                .user(user)
                .description(sb.toString()).build());
    }

    @Override
    @Transactional
    public void closeAccount(String userUid, String accountUid, String closingReason) {
        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));
        User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));

        validateAdmin(user, account); // note: this allows non-billing admin to close account, leaving for now but may revisit

        account.setEnabled(false);

        log.info("removing {} paid groups", account.getPaidGroups().size());
        Set<String> paidGroupUids = account.getPaidGroups().stream().map(Group::getUid).collect(Collectors.toSet());
        removeGroupsFromAccount(accountUid, paidGroupUids, userUid);

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.ACCOUNT_DISABLED)
                .description(closingReason).build());

        log.info("removing {} administrators", account.getAdministrators().size());

        account.getAdministrators().stream().filter(admin -> !admin.equals(user)).forEach(admin -> {
            admin.removeAccountAdministered(account);
            if (account.equals(admin.getPrimaryAccount()))
                admin.setPrimaryAccount(null);

            if (admin.getAccountsAdministered() == null || admin.getAccountsAdministered().isEmpty())
                permissionBroker.removeSystemRole(admin, StandardRole.ROLE_ACCOUNT_ADMIN);
        });

        removeAdministrator(userUid, accountUid, userUid, false); // at the end, remove self

        account.setClosed(true);
    }

    @Override
    @Transactional(readOnly = true)
    public long countAccountNotifications(String accountUid, Instant startTime, Instant endTime) {
        Account account = accountRepository.findOneByUid(accountUid);
        long groupNotifications = account.getPaidGroups().isEmpty() ? 0 :
                countAllForGroups(account.getPaidGroups(), startTime, endTime);

        final String accountLogOnlyQueryText = countQueryOpening() +
                "n.groupLog is null and n.eventLog is null and n.todoLog is null and n.campaignLog is null and " +
                "n.accountLog.account = :account";
        TypedQuery<Long> countNonGroupsQuery = entityManager.createQuery(accountLogOnlyQueryText, Long.class)
                .setParameter("start", startTime).setParameter("end", endTime).setParameter("account", account);
        long accountNotifications = countNonGroupsQuery.getSingleResult();
        log.info("Counted {} notifications for the account {}", accountNotifications, account.getName());

        return groupNotifications + accountNotifications;
    }

    @Override
    @Transactional(readOnly = true)
    public long countChargedNotificationsForGroup(String accountUid, String groupUid, Instant start, Instant end) {
        Group group = groupRepository.findOneByUid(groupUid);
        return countAllForGroups(Collections.singleton(group), start, end);
    }

    @Override
    public long countChargedUssdSessionsForAccount(String accountUid, Instant startTime, Instant endTime) {
        Account account = accountRepository.findOneByUid(accountUid);

        final String countQuery = "select count(distinct ul) from UserLog ul " +
                "where ul.creationTime between :start and :end and " +
                "ul.userLogType = :logType and ul.description in " +
                "(select c.uid from Campaign c where c.account = :account)";

        TypedQuery<Long> campaignSessionCount = entityManager.createQuery(countQuery, Long.class)
            .setParameter("start", startTime).setParameter("end", endTime).setParameter("account", account)
            .setParameter("logType", UserLogType.CAMPAIGN_ENGAGED);

        long countSessions = campaignSessionCount.getSingleResult();
        log.info("Counted {} campaign sessions for account", countSessions);

        if (locationInfoBroker != null && account.sponsorsDataSet()) {
            countSessions += countDataSetSessionsForAccount(accountUid, startTime, endTime);
        }

        return countSessions;
    }

    private long countDataSetSessionsForAccount(String accountUid, Instant start, Instant end) {
        // once have firm faith that both sides of this will match up, just use account list, but for now, this is not so expensive and is more reliable
        List<String> dataSetLabels = locationInfoBroker.getDatasetLabelsForAccount(accountUid);
        if (dataSetLabels == null || dataSetLabels.isEmpty())
            return 0;

        log.info("Counting sessions for data sets: {}", dataSetLabels);
        return countSessionsForDatasets(dataSetLabels, start, end);
    }

    @Override
    public List<Account> loadAllAccounts(boolean enabledOnly) {
        Sort sort = new Sort(Sort.Direction.ASC, "accountName");
        List<Account> accounts = !enabledOnly ? accountRepository.findAll(sort) :
                accountRepository.findAll(isEnabled(), sort);
        log.info("Retrieved {} accounts, for enabled = {}", accounts.size(), enabledOnly);
        return accounts;
    }

    @Override
    public Map<String, String> loadDisabledAccountMap() {
        Sort sort = new Sort(Sort.Direction.ASC, "accountName");
        List<Account> disabledAccounts = accountRepository.findAll(isDisabled(), sort);
        Map<String, String> accountMap = new LinkedHashMap<>();
        // to ensure we preserve ordering, doing this, vs collector
        disabledAccounts.forEach(account -> accountMap.put(account.getUid(), account.getAccountName()));
        return accountMap;
    }

    @Override
    public void updateDataSetLabels(String userUid, String accountUid, String dataSetLabels, boolean updateReferenceTables) {
        User admin = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        permissionBroker.validateSystemRole(admin, StandardRole.ROLE_SYSTEM_ADMIN);

        Account account = accountRepository.findOneByUid(Objects.requireNonNull(accountUid));

        final String oldLabels = account.getGeoDataSets();
        account.setGeoDataSets(dataSetLabels);

        if (updateReferenceTables) {
            Set<String> oldLabelList = StringUtils.commaDelimitedListToSet(oldLabels);
            Set<String> newLabelList = StringUtils.commaDelimitedListToSet(dataSetLabels);

            oldLabelList.removeAll(newLabelList); // i.e., leave behind those to remove
            newLabelList.removeAll(oldLabelList); // i.e., leave behind the new ones

            locationInfoBroker.updateDataSetAccountLabels(accountUid, newLabelList, oldLabelList);
        }

        createAndStoreSingleAccountLog(new AccountLog.Builder(account)
                .user(admin)
                .accountLogType(AccountLogType.GEO_API_UPDATED)
                .description(oldLabels + "-->>" + dataSetLabels).build());
    }

    @Override
    @Transactional(readOnly = true)
    public DataSetInfo fetchDataSetInfo(String userUid, String dataSetLabel, Instant start, Instant end) {
        User user = userRepository.findOneByUid(userUid);
        validateUserCanViewDataset(user, dataSetLabel);

        return DataSetInfo.builder()
                .dataSetLabel(dataSetLabel)
                .description(locationInfoBroker.getDescriptionForDataSet(dataSetLabel))
                .usersCount(countUsersForDataSets(Collections.singleton(dataSetLabel), start, end))
                .usersHistoryCount(countUsersForDataSets(Collections.singleton(dataSetLabel), DateTimeUtil.getEarliestInstant(), Instant.now()))
                .userSessionCount(countSessionsForDatasets(Collections.singleton(dataSetLabel), start, end))
                .notificationsCount(countNotificationsForDataSet(dataSetLabel, start, end))
                .start(start).end(end).build();
    }

    @Override
    @Transactional
    public void updateAccountSpendingLimit(final String userUid, final String accountUid, final long newAccountLimit) {
        final User user = userRepository.findOneByUid(userUid);
        final Account account = accountRepository.findOneByUid(accountUid);
        validateAdmin(user, account);
        final long oldLimit = account.getMonthlySpendingLimit();
        account.setMonthlySpendingLimit(newAccountLimit);
        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
                .accountLogType(AccountLogType.MONTHLY_LIMIT_CHANGED)
                .description("From " + oldLimit + " to " + newAccountLimit).build());
    }

    @Override
    @Transactional
    public void updateAccountUnitCosts(String userUid, String accountUid, int costPerUssdSession, int costPerSms) {
        final User user = userRepository.findOneByUid(userUid);
        permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);
        final Account account = accountRepository.findOneByUid(accountUid);
        final String fromRecord = "From " + account.getAvgUssdCost() + "c per USSD and " + account.getFreeFormCost() + "c per SMS ";
        account.setAvgUssdCost(costPerUssdSession);
        account.setFreeFormCost(costPerUssdSession);
        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
            .accountLogType(AccountLogType.AVG_COST_CHANGED)
            .description(fromRecord + "to " + costPerUssdSession + "c per USSD and " + costPerSms + "c per SMS").build());
    }

    @Override
    public void updateAccountMonthlyFlatFee(String userUid, String accountUid, long newFlatFee) {
        final User user = userRepository.findOneByUid(userUid);
        permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);
        final Account account = accountRepository.findOneByUid(accountUid);
        final long oldFee = account.getMonthlyFlatFee();
        account.setMonthlyFlatFee(newFlatFee);
        createAndStoreSingleAccountLog(new AccountLog.Builder(account).user(user)
            .accountLogType(AccountLogType.MONTHLY_FEE_CHANGED)
            .description("From " + oldFee + "c per month to " + newFlatFee + "c per month").build());
    }

    @Override
    @Transactional
    public void calculateAccountSpendingThisMonth(final String accountUid) {
        final Account account = accountRepository.findOneByUid(accountUid);

        final ZoneOffset zoneToUse = ZoneOffset.UTC;
        final Instant start = LocalDate.now().with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay().toInstant(zoneToUse);
        final Instant end = LocalDateTime.now().toInstant(zoneToUse);

        final long baseFee = account.getMonthlyFlatFee();

        // get counts of: (1) sent notifications, (2) USSD sessions, (3) monthly flat fee
        final long ussdSpend = countChargedUssdSessionsForAccount(accountUid, start, end) * account.getAvgUssdCost();
        final long smsSpend = countAccountNotifications(accountUid, start, end) * account.getFreeFormCost();

        final long totalSpent = baseFee + ussdSpend + smsSpend;

        log.info("For account {}, base fee of {}c, USSD spend of {}, and SMS spend of {} calculated total: {}",
                account.getName(), baseFee, ussdSpend, smsSpend, totalSpent);

        account.setCurrentMonthSpend(totalSpent);
    }

    private long countNotificationsForDataSet(String dataSetLabel, Instant start, Instant end) {
        final String accountLogOnlyQueryText = countQueryOpening() +
                "n.accountLog in (select al from AccountLog al where al.description like :dataSetStart)";
        TypedQuery<Long> countNonGroupsQuery = entityManager.createQuery(accountLogOnlyQueryText, Long.class)
                .setParameter("start", start).setParameter("end", end)
                .setParameter("dataSetStart", dataSetLabel + "%");
        long dataSetNotifications = countNonGroupsQuery.getSingleResult();
        log.info("Counted {} notifications for the dataset {}", dataSetNotifications, dataSetLabel);

        return dataSetNotifications;
    }

    private Specification<Account> isEnabled() {
        return (root, query, cb) -> cb.and(cb.isTrue(root.get(Account_.enabled)), cb.isFalse(root.get(Account_.closed)));
    }

    private Specification<Account> isDisabled() {
        return (root, query, cb) -> cb.and(cb.isFalse(root.get(Account_.enabled)), cb.isFalse(root.get(Account_.closed)));
    }

    private long countAllForGroups(Set<Group> groups, Instant start, Instant end) {
        long startTime = System.currentTimeMillis();
        long groupLogCount = executeGroupsCountQuery("(n.groupLog in (select gl from GroupLog gl where gl.group in :groups))",
                start, end, groups);

        long eventLogCount = executeGroupsCountQuery("(n.eventLog in (select el from EventLog el where el.event.ancestorGroup in :groups))",
                start, end, groups);

        long todoLogCount = executeGroupsCountQuery("(n.todoLog in (select tl from TodoLog tl where tl.todo.ancestorGroup in :groups))",
                start, end, groups);

        long campaignLogCount = executeGroupsCountQuery("(n.campaignLog in (select cl from CampaignLog cl where cl.campaign.masterGroup in :groups))",
                start, end, groups);

        log.debug("In {} msecs, for {} groups, counted {} from group logs, {} from event logs, {} from todo logs, {} from campaign logs",
                System.currentTimeMillis() - startTime, groups.size(), groupLogCount, eventLogCount, todoLogCount, campaignLogCount);

        return groupLogCount + eventLogCount + todoLogCount + campaignLogCount;

    }

    private long executeGroupsCountQuery(String querySuffix, Instant start, Instant end, Set<Group> groups) {
        TypedQuery<Long> countQuery = entityManager.createQuery(countQueryOpening() + querySuffix, Long.class)
                .setParameter("start", start).setParameter("end", end)
                .setParameter("groups", groups);

        return countQuery.getSingleResult();
    }

    private String countQueryOpening() {
        return "select count(n) from Notification n " +
                "where n.createdDateTime between :start and :end and " +
                "n.status in ('DELIVERED', 'READ') and ";
    }

    private long countSessionsForDatasets(Collection<String> dataSets, Instant start, Instant end) {
        return countForDataSets("select count(distinct ul) from UserLog ul", dataSets, start, end);
    }

    private long countUsersForDataSets(Collection<String> dataSets, Instant start, Instant end) {
        return countForDataSets("select count(distinct ul.userUid) from UserLog ul", dataSets, start, end);
    }

    private long countForDataSets(String prefix, Collection<String> dataSets, Instant start, Instant end) {
        final String countGeoSessionsQuery = prefix + " where ul.creationTime between :start and :end and " +
                "ul.userLogType = :logType and ul.description in :dataSets";
        TypedQuery<Long> geoApiSessionCount = entityManager.createQuery(countGeoSessionsQuery, Long.class)
                .setParameter("start", start).setParameter("end", end)
                .setParameter("logType", UserLogType.GEO_APIS_CALLED).setParameter("dataSets", dataSets);
        long countGeoCalls = geoApiSessionCount.getSingleResult();
        log.info("Counted {} sessions for dataSets {}, prefix {}", countGeoCalls, dataSets, prefix);
        return countGeoCalls;
    }

    private void validateUserCanViewDataset(User user, String dataSetLabel) {
        Set<String> accountUids = locationInfoBroker.getAccountUidsForDataSets(dataSetLabel);
        List<Account> accounts = accountRepository.findByUidIn(accountUids);

        if (accounts == null || accounts.isEmpty())
            throw new IllegalArgumentException("Error! No accounts found for dataset " + dataSetLabel);

        boolean primaryAccountMatches = accounts.contains(user.getPrimaryAccount());
        if (!primaryAccountMatches && !permissionBroker.isSystemAdmin(user)) {
            Set<Account> userAccounts = user.getAccountsAdministered();
            if (accounts.stream().noneMatch(userAccounts::contains))
                throw new AccessDeniedException("Only account admins of sponsoring datasets can see stats");
        }
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }
}