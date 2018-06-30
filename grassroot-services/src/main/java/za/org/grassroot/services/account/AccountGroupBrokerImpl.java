package za.org.grassroot.services.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.GroupWelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.services.MessageAssemblingService;
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
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
    private final BroadcastRepository templateRepository;
    private final MessageAssemblingService messageAssemblingService;

    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public AccountGroupBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, TodoRepository todoRepository,
                                  EventRepository eventRepository, PermissionBroker permissionBroker, AccountRepository accountRepository,
                                  BroadcastRepository templateRepository, MessageAssemblingService messageAssemblingService, LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.todoRepository = todoRepository;
        this.eventRepository = eventRepository;
        this.permissionBroker = permissionBroker;
        this.accountRepository = accountRepository;
        this.templateRepository = templateRepository;
        this.messageAssemblingService = messageAssemblingService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    @PostConstruct
    public void init() {
        LocalDate ldEventLimitStart;
        try {
            ldEventLimitStart = LocalDate.parse(eventLimitStartString, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            logger.error("Error parsing! {}", e);
            ldEventLimitStart = LocalDate.of(2017, 4, 1);
        }
        eventLimitStart = ldEventLimitStart.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private void validateAdmin(User user, Account account) {
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
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

        if (group == null) {
            throw new GroupNotFoundException();
        }

        if (group.isPaidFor()) {
            throw new GroupAlreadyPaidForException();
        }

        account.addPaidGroup(group);
        group.setPaidFor(true);
        storeGroupAddOrRemoveLogs(AccountLogType.GROUP_ADDED, account, group, group.getUid(), addingUser);
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
                Specification.where(GroupSpecifications.createdByUser(user))
                .and(GroupSpecifications.isActive())
                .and(GroupSpecifications.paidForStatus(false)));
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
            return group.getAccount();
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
            User user = userRepository.findOneByUid(removingUserUid);

            if (!account.getAdministrators().contains(user)) {
                permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
            }

            account.removePaidGroup(group);
            group.setPaidFor(false);

            bundle.addLog(new AccountLog.Builder(account)
                    .user(user)
                    .accountLogType(AccountLogType.GROUP_REMOVED)
                    .group(group)
                    .paidGroupUid(group.getUid())
                    .description(group.getName()).build());

            bundle.addLog(new GroupLog(group,
                    user,
                    GroupLogType.REMOVED_FROM_ACCOUNT,
                    null, null, account, removedDescription));
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberTodosLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);

        boolean isSmallGroup = group.getMemberships().size() < eventMonthlyLimitThreshold;
        boolean isOnAccount = group.isPaidFor() && group.getAccount() != null && group.getAccount().isEnabled();

        if (isSmallGroup || isOnAccount)
            return LARGE_EVENT_LIMIT;

        int todosThisMonth = (int) todoRepository.count(Specification.where(hasGroupAsAncestor(group))
                .and(createdDateBetween(LocalDateTime.now().withDayOfMonth(1).withHour(0).toInstant(ZoneOffset.UTC), Instant.now())));

        return Math.max(0, FREE_TODOS_PER_MONTH - todosThisMonth);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberEventsLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        boolean isSmallGroup = group.getMemberships().size() < eventMonthlyLimitThreshold;
        boolean isOnAccount = group.isPaidFor() && group.getAccount() != null && group.getAccount().isEnabled();
        if (isSmallGroup || isOnAccount) {
            return LARGE_EVENT_LIMIT;
        } else {
            Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);
            Instant startOfCheck = startOfMonth.isAfter(eventLimitStart) ? startOfMonth : eventLimitStart;

            int eventsThisMonth = (int) eventRepository.count(Specification.where(EventSpecifications.hasGroupAsAncestor(group))
                    .and(EventSpecifications.createdDateTimeBetween(startOfCheck, Instant.now())));

            return Math.max(0, FREE_EVENTS_PER_MONTH - eventsThisMonth);
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
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(messages);

        User user = userRepository.findOneByUid(userUid);
        Account account = accountUid == null ? user.getPrimaryAccount() : accountRepository.findOneByUid(accountUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (account == null) {
            throw new NoPaidAccountException();
        }

        logger.info("going to check for paid group on account = {}, for group = {}", account.getName(), group.getName());

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
                .paidGroupUid(group.getUid())
                .user(user)
                .description(String.format("Duration: %s, messages: %s", delayToSend == null ? "none" : delayToSend.toString(), messages.toString()))
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
                .delayIntervalMillis(delayToSend == null ? 0L : delayToSend.toMillis())
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
                .paidGroupUid(group.getUid())
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

        validateAdmin(user, account);

        Broadcast template = templateRepository.findTopByGroupAndBroadcastScheduleAndActiveTrue(group,
                BroadcastSchedule.ADDED_TO_GROUP);

        template.setActive(false);

        storeAccountLogPostCommit(new AccountLog.Builder(account)
                .accountLogType(AccountLogType.GROUP_WELCOME_DEACTIVATED)
                .group(group)
                .paidGroupUid(group.getUid())
                .user(user).build());

    }

    @Override
    @Transactional(readOnly = true)
    public boolean hasGroupWelcomeMessages(String groupUid) {
        return loadWelcomeMessage(groupUid) != null;
    }

    @Override
    @Transactional(readOnly = true)
    public Broadcast loadWelcomeMessage(String groupUid) {
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
                    .paidGroupUid(group.getUid())
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
    public String generateGroupWelcomeReply(String userUid, String groupUid) {
        final User user = userRepository.findOneByUid(Objects.requireNonNull(userUid));
        final Group group = groupRepository.findOneByUid(Objects.requireNonNull(groupUid));
        final Account groupAccount = findAccountForGroup(groupUid);

        final String userMessage = messageAssemblingService.createGroupJoinedMessage(user, group);
        if (groupAccount != null) {
            createAndStoreSingleAccountLog(new AccountLog.Builder(groupAccount)
                    .accountLogType(AccountLogType.MESSAGE_SENT)
                    .user(user).group(group).build());
        }
        return userMessage;
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

}
