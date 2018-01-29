package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.account.PaidGroup;
import za.org.grassroot.core.domain.notification.FreeFormMessageNotification;
import za.org.grassroot.core.domain.notification.GroupWelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.PaidGroupStatus;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.*;
import za.org.grassroot.services.util.FullTextSearchUtils;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static org.springframework.data.jpa.domain.Specifications.where;
import static za.org.grassroot.core.specifications.NotificationSpecifications.*;
import static za.org.grassroot.core.specifications.PaidGroupSpecifications.expiresAfter;
import static za.org.grassroot.core.specifications.PaidGroupSpecifications.isForAccount;
import static za.org.grassroot.core.specifications.TodoSpecifications.createdDateBetween;
import static za.org.grassroot.core.specifications.TodoSpecifications.hasGroupAsAncestor;

/**
 * Created by luke on 2016/10/25.
 */
@Service
public class AccountGroupBrokerImpl extends AccountBrokerBaseImpl implements AccountGroupBroker {

    private static final Logger logger = LoggerFactory.getLogger(AccountGroupBrokerImpl.class);

    @Value("${accounts.todos.monthly.free:4}")
    private int FREE_TODOS_PER_MONTH;

    @Value("${accounts.events.monthly.free:4}")
    private int FREE_EVENTS_PER_MONTH;

    @Value("${grassroot.events.limit.threshold:100}")
    private int eventMonthlyLimitThreshold;

    @Value("${grassroot.events.limit.started:2017-04-01}")
    private String eventLimitStartString;
    private Instant eventLimitStart;

    private static final int LARGE_EVENT_LIMIT = 99;
    private static final long WELCOME_MSG_INTERVAL = 60 * 1000; // 1 minute

    private static final String addedDescription = "Group added to Grassroot Extra";
    private static final String removedDescription = "Group removed from Grassroot Extra";

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PermissionBroker permissionBroker;
    private final TodoRepository todoRepository;
    private final EventRepository eventRepository;

    private final AccountRepository accountRepository;
    private final PaidGroupRepository paidGroupRepository;
    private final BroadcastRepository templateRepository;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public AccountGroupBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, TodoRepository todoRepository,
                                  EventRepository eventRepository, PermissionBroker permissionBroker, AccountRepository accountRepository,
                                  PaidGroupRepository paidGroupRepository, BroadcastRepository templateRepository, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.todoRepository = todoRepository;
        this.eventRepository = eventRepository;
        this.permissionBroker = permissionBroker;
        this.accountRepository = accountRepository;
        this.paidGroupRepository = paidGroupRepository;
        this.templateRepository = templateRepository;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @PostConstruct
    public void init() {
        LocalDate ldEventLimitStart;
        try {
            ldEventLimitStart = LocalDate.parse(eventLimitStartString, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            logger.error("Error parsing! {}", e);
            ldEventLimitStart = LocalDate.of(2017, 04, 01);
        }
        eventLimitStart = ldEventLimitStart.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private void validateAdmin(User user, Account account) {
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> fetchGroupsSponsoredByAccount(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);
        List<PaidGroup> paidGroups = paidGroupRepository.findAll(
                where(isForAccount(account))
                .and(expiresAfter(Instant.now())));
        List<Group> groups = new ArrayList<>();
        if (paidGroups != null) {
            groups = paidGroups.stream()
                    .map(PaidGroup::getGroup)
                    .distinct()
                    .sorted(Comparator.comparing(Group::getName))
                    .collect(Collectors.toList());
        }
        return groups;
    }

    @Override
    @Transactional
    public void addGroupToUserAccount(String groupUid, String userUid) {
        try {
            addGroupToAccount(null, groupUid, userUid);
        } catch(GroupAlreadyPaidForException|IllegalArgumentException|AccountExpiredException|AccountLimitExceededException|GroupNotFoundException e) {
            logger.error("could not add group to account", e);
        }
    }

    @Override
    @Transactional
    public void addGroupToAccount(String accountUid, String groupUid, String addingUserUid) throws GroupAlreadyPaidForException {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(addingUserUid);

        Group group = groupRepository.findOneByUid(groupUid);
        User addingUser = userRepository.findOneByUid(addingUserUid);

        Account account = StringUtils.isEmpty(accountUid) ? addingUser.getPrimaryAccount() : accountRepository.findOneByUid(accountUid);

        if (account == null) {
            throw new IllegalArgumentException("Error! Account UID not supplied and user does not have an account");
        }

        validateAdmin(addingUser, account);

        if (!account.isEnabled()) {
            throw new AccountExpiredException();
        }

        if (numberGroupsLeft(account.getUid()) < 1) {
            throw new AccountLimitExceededException();
        }

        if (group == null) {
            throw new GroupNotFoundException();
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
    @Transactional
    public void addGroupsToAccount(String accountUid, Set<String> groupUids, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUids);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);
        validateAdmin(user, account);

        List<Group> groups = groupRepository.findAll(where(GroupSpecifications.uidIn(groupUids)));
        logger.info("number of groups matching list: {}", groups.size());

        addGroupsToAccount(groups, account, user);
    }

    @Override
    @Transactional
    public int addUserCreatedGroupsToAccount(String accountUid, String userUid) {
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(userUid);

        Account account = accountRepository.findOneByUid(accountUid);
        User user = userRepository.findOneByUid(userUid);

        if (!account.getAdministrators().contains(user)) {
            throw new IllegalArgumentException("Error! Add all groups can only be called by an admin of the account");
        }

        List<Group> groups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user);
        return addGroupsToAccount(groups, account, user);
    }

    private int addGroupsToAccount(List<Group> groups, Account account, User user) {
        DebugUtil.transactionRequired("AddingGroupsToAccount");

        List<PaidGroup> paidGroups = new ArrayList<>();
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        int spaceOnAccount = groupsLeftOnAccount(account);

        for (int i = 0; i < groups.size() && (spaceOnAccount - i) > 0; i++) {
            Group group = groups.get(i);
            if (group.isPaidFor()) {
                continue;
            }

            PaidGroup paidGroup = new PaidGroup(group, account, user);
            paidGroups.add(paidGroup);
            account.addPaidGroup(paidGroup);
            group.setPaidFor(true);

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_ADDED)
                    .group(group)
                    .paidGroupUid(paidGroup.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group, user, GroupLogType.ADDED_TO_ACCOUNT,
                    null, null, account, addedDescription));
        }

        paidGroupRepository.save(paidGroups);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);

        return paidGroups.size();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> searchGroupsForAddingToAccount(String userUid, String accountUid, String filterTerm) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);

        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        String tsQuery = FullTextSearchUtils.encodeAsTsQueryText(filterTerm == null ? "" : filterTerm, true, true);
        List<Group> userGroups = groupRepository.findByActiveAndMembershipsUserWithNameContainsText(user.getId(), tsQuery);

        logger.info("number of user groups: {}", userGroups.size());

        return userGroups.stream()
                .filter(g -> !g.isPaidFor())
                .collect(Collectors.toList());
    }

    @Override
    public List<Group> fetchUserCreatedGroupsUnpaidFor(String userUid, Sort sort) {
        User user = userRepository.findOneByUid(userUid);
        return groupRepository.findAll(
                where(GroupSpecifications.createdByUser(user))
                .and(GroupSpecifications.isActive())
                .and(GroupSpecifications.paidForStatus(false)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAddGroupToAccount(String userUid, String accountUid) {
        Account account;
        if (StringUtils.isEmpty(accountUid)) {
            User user = userRepository.findOneByUid(userUid);
            account = user.getPrimaryAccount();
        } else {
            account = accountRepository.findOneByUid(accountUid);
        }
        return account != null && account.isEnabled() && groupsLeftOnAccount(account) > 0;
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAddAllCreatedGroupsToAccount(String userUid, String accountUid) {
        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        if (user.getPrimaryAccount() == null) {
            return false;
        } else {
            List<Group> userGroups = groupRepository.findAll(
                    where(GroupSpecifications.createdByUser(user))
                    .and(GroupSpecifications.paidForStatus(false))
                    .and(GroupSpecifications.isActive()));

            logger.info("number of user groups: {}, groups left on account: {}", userGroups.size(), groupsLeftOnAccount(account));
            return userGroups.size() < groupsLeftOnAccount(account);
        }
    }

    private int groupsLeftOnAccount(Account account) {
        return account.getMaxNumberGroups() -
                (int) paidGroupRepository.count(where(expiresAfter(Instant.now())).and(isForAccount(account)));
    }

    @Override
    public boolean isGroupOnAccount(String groupUid) {
        return findAccountForGroup(groupUid) != null;
    }

    @Override
    @Transactional(readOnly =  true)
    public Account findAccountForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        if (!group.isPaidFor()) {
            return null;
        } else {
            PaidGroup latestRecord = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group);
            return latestRecord == null ? null : latestRecord.getAccount();
        }
    }

    @Override
    public void validateUserAccountAdminForGroup(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Account account = findAccountForGroup(groupUid);

        if (account == null) {
            throw new GroupNotPaidForException();
        }

        if (!account.getAdministrators().contains(user)) {
            throw new AccessDeniedException("User is not an administrator of the group account");
        }
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
            PaidGroup record = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group);
            User user = userRepository.findOneByUid(removingUserUid);

            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }

            record.setExpireDateTime(Instant.now());
            record.setRemovedByUser(user);
            account.removePaidGroup(record);
            group.setPaidFor(false);

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_REMOVED)
                    .group(group)
                    .paidGroupUid(record.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group,
                    user,
                    GroupLogType.REMOVED_FROM_ACCOUNT,
                    null, null, account, removedDescription));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public void sendFreeFormMessage(String userUid, String groupUid, String message) {
        // for now, just let the notification async handle the group loading etc., here just check the user
        // has permission (is account admin--later, account admin and it's a paid group, with enough credit

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        Account account = user.getPrimaryAccount();
        PaidGroup paidGroup = paidGroupRepository.findTopByGroupAndStatusOrderByActiveDateTimeDesc(group, PaidGroupStatus.ACTIVE);

        Objects.requireNonNull(user);
        Objects.requireNonNull(group);
        Objects.requireNonNull(account);
        Objects.requireNonNull(paidGroup);

        authorizeFreeFormMessageSending(user, account, group, paidGroup);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        String description = group.getMembers().size() + " members @ : " + account.getFreeFormCost(); // so it's recorded at cost of sending

        AccountLog accountLog = new AccountLog.Builder(account)
                .user(user)
                .accountLogType(AccountLogType.MESSAGE_SENT)
                .group(group)
                .paidGroupUid(paidGroup.getUid())
                .description(description)
                .build();

        bundle.addLog(accountLog);
        for (User member : group.getMembers()) {
            bundle.addNotification(new FreeFormMessageNotification(member, message, accountLog));
        }

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberGroupsLeft(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);
        return account == null ? 0 : account.isEnabled() ? groupsLeftOnAccount(account) : 0;
    }

    @Override
    @Transactional(readOnly = true)
    public int numberTodosLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        int todosThisMonth = (int) todoRepository.count(where(hasGroupAsAncestor(group))
                .and(createdDateBetween(LocalDateTime.now().withDayOfMonth(1).withHour(0).toInstant(ZoneOffset.UTC), Instant.now())));

        int monthlyLimit;
        if (!group.isPaidFor()) {
            monthlyLimit = FREE_TODOS_PER_MONTH;
        } else {
            try {
                Account account = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group).getAccount();
                monthlyLimit = account.getTodosPerGroupPerMonth();;
            } catch (NullPointerException e) {
                logger.warn("Error! Group is marked as paid for but has no paid group record associated to it");
                monthlyLimit = FREE_TODOS_PER_MONTH;
            }
        }

        return monthlyLimit - todosThisMonth;
    }

    @Override
    @Transactional(readOnly = true)
    public int numberEventsLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);

        if (group.getMemberships().size() < eventMonthlyLimitThreshold) {
            return LARGE_EVENT_LIMIT;
        } else {

            Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant startOfCheck = startOfMonth.isAfter(eventLimitStart) ? startOfMonth : eventLimitStart;

            int eventsThisMonth = (int) eventRepository.count(where(EventSpecifications.hasGroupAsAncestor(group))
                    .and(EventSpecifications.createdDateTimeBetween(startOfCheck, Instant.now())));
            int monthlyLimit;

            if (!group.isPaidFor()) {
                monthlyLimit = FREE_EVENTS_PER_MONTH;
            } else {
                try {
                    Account account = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group).getAccount();
                    monthlyLimit = account.getEventsPerGroupPerMonth();
                } catch (NullPointerException e) {
                    logger.warn("Error! Group is marked as paid for but has no paid group record associated to it");
                    monthlyLimit = FREE_EVENTS_PER_MONTH;
                }
            }

            logger.info("Counting events left, limit: {}, this month: {}", monthlyLimit, eventsThisMonth);
            return Math.max(0, monthlyLimit - eventsThisMonth);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int numberEventsLeftForParent(String eventUid) {
        Event event = eventRepository.findOneByUid(eventUid);
        return numberEventsLeftForGroup(event.getAncestorGroup().getUid());
    }

    @Override
    @Transactional
    public void createGroupWelcomeMessages(String userUid, String accountUid, String groupUid,
                                           List<String> messages, Duration delayToSend, Locale language, boolean onlyViaFreeChannels) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(accountUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(messages);
        Objects.requireNonNull(delayToSend);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountRepository.findOneByUid(accountUid);
        Group group = groupRepository.findOneByUid(groupUid);

        logger.info("account = {}", account);
        logger.info("group = {}", group);

        logger.info("going to check for paid group on account = {}, for group = {}", account.getName(), group.getName());
        PaidGroup paidGroup = getOrCreatePaidGroup(user, account, group);

        if (!account.isBillPerMessage()) {
            throw new AccountLimitExceededException();
        }

        if (messages.isEmpty()) {
            throw new IllegalArgumentException("Notification templates need at least one message");
        }

        if (messages.size() > Broadcast.MAX_MESSAGES) {
            throw new IllegalArgumentException("Notification templates can only store up to 3 messages");
        }

        storeAccountLogPostCommit(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.GROUP_WELCOME_MESSAGES_CREATED)
                .group(group)
                .paidGroupUid(paidGroup.getUid())
                .user(user)
                .description(String.format("Duration: %s, messages: %s", delayToSend.toString(), messages.toString()))
                .build());

        // note : could also throw an error here, but would have to be more confident in the overall Account structure's
        // ability to then allow for finding the other one and disabling it (i.e., to resolve such conflicts)
        checkForAndDisablePriorTemplate(group, user);

        Broadcast template = Broadcast.builder()
                .account(account)
                .group(group)
                .broadcastSchedule(BroadcastSchedule.ADDED_TO_GROUP)
                .createdByUser(user)
                .active(true)
                .creationTime(Instant.now())
                .delayIntervalMillis(delayToSend.toMillis())
                .smsTemplate1(messages.get(0))
                .onlyUseFreeChannels(onlyViaFreeChannels)
                .build();

        if (messages.size() > 1) {
            template.setSmsTemplate2(messages.get(1));
        }

        if (messages.size() > 2) {
            template.setSmsTemplate3(messages.get(2));
        }

        templateRepository.save(template);
    }

    private void checkForAndDisablePriorTemplate(Group group, User user) {
        Broadcast template = templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group,
                BroadcastSchedule.ADDED_TO_GROUP);
        if (template != null) {
            logger.error("Conflict in group welcome messages ... check logs and debug");
            storeAccountLogPostCommit(new AccountLog.Builder(template.getAccount())
                    .user(user)
                    .group(group)
                    .accountLogType(AccountLogType.GROUP_WELCOME_CONFLICT)
                    .description("Group welcome messages deactivated due to conflicting creation of new template").build());
            template.setActive(false);
        }
    }

    @Override
    @Transactional
    public void updateGroupWelcomeNotifications(String userUid, String groupUid, List<String> messages, Duration delayToSend) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(messages);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = findAccountForGroup(groupUid);
        PaidGroup paidGroup = fetchLatestPaidGroup(group);

        validateAdmin(user, account);

        // todo : as above, checking for uniqueness, disabling, etc
        Broadcast template = templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group,
                BroadcastSchedule.ADDED_TO_GROUP);

        boolean changedTime = false;
        if (delayToSend != null && delayToSend != Duration.of(template.getDelayIntervalMillis(), ChronoUnit.MILLIS)) {
            template.setDelayIntervalMillis(delayToSend.toMillis());
            changedTime = true;
        }

        boolean changedMessages = !messages.equals(template.getTemplateStrings());
        if (changedMessages) {
            template.setSmsTemplate1(messages.get(0));
            if (messages.size() > 1) {
                template.setSmsTemplate2(messages.get(1));
            } else if (!StringUtils.isEmpty(template.getSmsTemplate2())) {
                template.setSmsTemplate2(null);
            }
            if (messages.size() > 2) {
                template.setSmsTemplate3(messages.get(2));
            } else if (!StringUtils.isEmpty(template.getSmsTemplate3())) {
                template.setSmsTemplate3(null);
            }
        }

        final String logDescription = (changedTime ? String.format("Duration: %s,", delayToSend.toString()) : "") +
                (changedMessages ? String.format("Messages: %s", messages.toString()) : "");
        storeAccountLogPostCommit(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.GROUP_WELCOME_MESSAGES_CHANGED)
                .group(group)
                .paidGroupUid(paidGroup.getUid())
                .user(user)
                .description(logDescription).build());
    }

    @Override
    @Transactional
    public void deactivateGroupWelcomes(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = findAccountForGroup(groupUid);
        PaidGroup paidGroup = fetchLatestPaidGroup(group);

        validateAdmin(user, account);

        Broadcast template = templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group,
                BroadcastSchedule.ADDED_TO_GROUP);

        template.setActive(false);

        storeAccountLogPostCommit(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.GROUP_WELCOME_DEACTIVATED)
                .group(group)
                .paidGroupUid(paidGroup.getUid())
                .user(user).build());

    }

    @Override
    @Transactional(readOnly = true)
    public Broadcast loadTemplate(String groupUid) {
        Objects.requireNonNull(groupUid);

        Account account = findAccountForGroup(groupUid);
        if (account == null) {
            return null;
        }

        Group group = groupRepository.findOneByUid(groupUid);
        return templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group,
                BroadcastSchedule.ADDED_TO_GROUP);
    }

    @Override
    @Transactional
    public void generateGroupWelcomeNotifications(String addingUserUid, String groupUid, Set<String> addedMemberUids) {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(addingUserUid);
        Objects.requireNonNull(addedMemberUids);

        DebugUtil.transactionRequired("");

        Account account = findAccountForGroup(groupUid);
        if (account == null) {
            throw new IllegalArgumentException("Trying to create welcome notifications for non-account linked group");
        }

        Group group = groupRepository.findOneByUid(groupUid);
        PaidGroup latestRecord = paidGroupRepository.findTopByGroupOrderByExpireDateTimeDesc(group);
        Broadcast template = checkForGroupTemplate(group);

        // note: at some point do this recursively, but for the moment, a one level check is fine
        if ((template == null || !template.isActive()) && group.getParent() != null) {
            template = checkForGroupTemplate(group.getParent());
        }

        if (template != null && template.isActive()) {
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

            AccountLog accountLog = new AccountLog.Builder(account)
                    .user(userRepository.findOneByUid(addingUserUid))
                    .group(group)
                    .paidGroupUid(latestRecord.getUid())
                    .accountLogType(AccountLogType.MESSAGE_SENT)
                    .description("Generated group welcome notifications for {} members") // todo : more descriptions
                    .build();

            bundle.addLog(accountLog);
            for (int i = 0; i < template.getTemplateStrings().size(); i++) {
                bundle.addNotifications(generateNotifications(template, i, group, addedMemberUids, accountLog));
            }

            logsAndNotificationsBroker.asyncStoreBundle(bundle);
            logger.info("sent {} logs and {} notifications for storage, exiting", bundle.getLogs().size(), bundle.getNotifications().size());
        }
    }

    private Broadcast checkForGroupTemplate(Group group) {
        return templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group, BroadcastSchedule.ADDED_TO_GROUP);
    }

    @Override
    @Transactional
    public void cascadeWelcomeMessages(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        validateUserAccountAdminForGroup(userUid, groupUid);

        Broadcast template = loadTemplate(groupUid);
        template.setCascade(true);

        createAndStoreSingleAccountLog(new AccountLog.Builder(template.getAccount())
                .user(userRepository.findOneByUid(userUid))
                .group(template.getGroup())
                .accountLogType(AccountLogType.GROUP_WELCOME_CASCADE_ON)
                .build());
    }

    @Override
    @Transactional
    public void disableCascadingMessages(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        validateUserAccountAdminForGroup(userUid, groupUid);

        Broadcast template = loadTemplate(groupUid);
        template.setCascade(false);

        createAndStoreSingleAccountLog(new AccountLog.Builder(template.getAccount())
                .user(userRepository.findOneByUid(userUid))
                .group(template.getGroup())
                .accountLogType(AccountLogType.GROUP_WELCOME_CASCADE_OFF)
                .build());
    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasSubgroups(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return group.getDirectChildren() != null && !group.getDirectChildren().isEmpty();
    }

    private Set<Notification> generateNotifications(Broadcast templateEntity, int templateStringIndex,
                                                    Group group, Set<String> memberUids, AccountLog accountLog) {
        logger.debug("generating notifications for {} member", memberUids.size());
        Instant now = Instant.now().plus(templateStringIndex * WELCOME_MSG_INTERVAL, ChronoUnit.MILLIS);
        Set<Notification> notifications = userRepository.findByUidIn(memberUids).stream()
                .map(user -> fromTemplate(templateEntity, templateStringIndex, group.getMembership(user), accountLog, now))
                .collect(Collectors.toSet());
        logger.debug("generated {} notifications", notifications.size());
        return notifications;
    }

    private Notification fromTemplate(Broadcast template, int templateStringIndex,
                                      Membership membership, AccountLog accountLog, Instant referenceTime) {
        // todo : handle truncating better, handle null delays, handle send only via free
        Notification notification = new GroupWelcomeNotification(membership.getUser(),
                messageFromTemplateString(template.getTemplateStrings().get(templateStringIndex), membership, 160), accountLog);
        notification.setSendOnlyAfter(referenceTime.plus(template.getDelayIntervalMillis(), ChronoUnit.MILLIS));
        return notification;
    }

    private String messageFromTemplateString(String template, Membership membership, int maxChars) {
        final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("d MMM, yyyy"); // can consider letting user define in future
        final String formatString = template
                .replace("__name__", "%1$s")
                .replace("__date__", "%2$s");
        final String joinedDateString = DateTimeUtil.formatAtSAST(membership.getJoinTime(), formatter);
        String message = String.format(formatString, membership.getUser().getName(), joinedDateString);
        return message.substring(0, Math.min(message.length(), maxChars));
    }

    private PaidGroup getOrCreatePaidGroup(User user, Account account, Group group) {
        PaidGroup paidGroup = fetchLatestPaidGroup(group);
        if (paidGroup == null) {
            logger.info("found no paid group record, creating one ...");
            addGroupToAccount(account.getUid(), group.getUid(), user.getUid());
            paidGroup = fetchLatestPaidGroup(group);
        } else if (!paidGroup.getAccount().equals(account)) {
            throw new GroupAlreadyPaidForException();
        }

        return paidGroup;
    }

    private PaidGroup fetchLatestPaidGroup(Group group) {
        return paidGroupRepository.findTopByGroupAndStatusOrderByActiveDateTimeDesc(group, PaidGroupStatus.ACTIVE);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberMessagesLeft(String accountUid) {
        Account account = accountRepository.findOneByUid(accountUid);

        long messagesThisMonth = logsAndNotificationsBroker.countNotifications(where(
                accountLogTypeIs(AccountLogType.MESSAGE_SENT))
                .and(belongsToAccount(account))
                .and(createdTimeBetween(LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC), Instant.now())));

        return Math.max(0, account.getFreeFormMessages() - (int) messagesThisMonth);
    }

    private void storeGroupAddOrRemoveLogs(AccountLogType accountLogType, Account account, Group group, String paidGroupUid, User user) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        bundle.addLog(new AccountLog.Builder(account)
                .user(user)
                .accountLogType(accountLogType)
                .group(group)
                .paidGroupUid(paidGroupUid)
                .description(group.getName()).build());

        final boolean isAdded = AccountLogType.GROUP_ADDED.equals(accountLogType);
        bundle.addLog(new GroupLog(group,
                user,
                isAdded ? GroupLogType.ADDED_TO_ACCOUNT : GroupLogType.REMOVED_FROM_ACCOUNT,
                null, null, account,
                isAdded ? addedDescription : removedDescription));
        logsAndNotificationsBroker.storeBundle(bundle);
    }

    private void authorizeFreeFormMessageSending(User user, Account account, Group group, PaidGroup paidGroup) {
        logger.info("Authorizing message, paid group = {}, group = {}", paidGroup, group);

        if (account == null || !account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        if (!group.isPaidFor() || !paidGroup.isActive()) {
            throw new GroupNotPaidForException();
        }

        if (!paidGroup.getAccount().equals(account)) {
            throw new GroupAccountMismatchException();
        }

        if (group.getMemberships().size() > numberMessagesLeft(account.getUid())) {
            throw new AccountLimitExceededException();
        }
    }

}
