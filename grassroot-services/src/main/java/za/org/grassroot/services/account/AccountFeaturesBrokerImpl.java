package za.org.grassroot.services.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.GroupWelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.events.AlterConfigVariableEvent;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.EventSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.exception.AccountLimitExceededException;
import za.org.grassroot.services.exception.NoPaidAccountException;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import javax.annotation.PostConstruct;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.TodoSpecifications.createdDateBetween;
import static za.org.grassroot.core.specifications.TodoSpecifications.hasGroupAsAncestor;

/**
 * Created by luke on 2016/10/25.
 */
@Service @Slf4j
public class AccountFeaturesBrokerImpl implements AccountFeaturesBroker, ApplicationListener<AlterConfigVariableEvent> {

    private static final int LARGE_EVENT_LIMIT = 99;
    private static final long WELCOME_MSG_INTERVAL = 60 * 1000; // 1 minute

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PermissionBroker permissionBroker;
    private final TodoRepository todoRepository;
    private final EventRepository eventRepository;
    private final MembershipRepository membershipRepository;

    private final AccountRepository accountRepository;
    private final BroadcastRepository templateRepository;
    private final MessageAssemblingService messageAssemblingService;
    private final ConfigRepository configRepository;

    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private ApplicationEventPublisher eventPublisher;

    private Map<String, String> configVariables = new HashMap<>();

    @Autowired
    public AccountFeaturesBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, MembershipRepository membershipRepository, TodoRepository todoRepository,
                                     EventRepository eventRepository, PermissionBroker permissionBroker, AccountRepository accountRepository,
                                     BroadcastRepository templateRepository, MessageAssemblingService messageAssemblingService,
                                     LogsAndNotificationsBroker logsAndNotificationsBroker, ConfigRepository configRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.membershipRepository = membershipRepository;
        this.todoRepository = todoRepository;
        this.eventRepository = eventRepository;
        this.permissionBroker = permissionBroker;
        this.accountRepository = accountRepository;
        this.templateRepository = templateRepository;
        this.messageAssemblingService = messageAssemblingService;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.configRepository = configRepository;
    }

    @Autowired
    public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @PostConstruct
    public void init() {
        log.info("Setting up account features, populating config variable map");
        Map<String, String> configDefaults = new HashMap<>();
        configDefaults.put("group.size.limited", "false");
        configDefaults.put("group.joins.limited", "false");
        configDefaults.put("group.size.freemax", "300");
        configDefaults.put("tasks.monthly.free", "4");
        configDefaults.put("tasks.limit.threshold", "10");
        configDefaults.put("welcome.messages.on", "false");

        configDefaults.forEach((key, defaultValue) -> configVariables.put(key, convertKeyToValue(key, defaultValue)));
        log.info("Populated account features config variable map : {}", configVariables);
    }

    private String convertKeyToValue(String key, String defaultValue) {
        return configRepository.findOneByKey(key).map(ConfigVariable::getValue).orElse(defaultValue);
    }

    @Override
    public void onApplicationEvent(AlterConfigVariableEvent event) {
        log.info("Received notice of some change in config variables: ", event);
        if (!StringUtils.isEmpty(event.getKey())) {
            configRepository.findOneByKey(event.getKey()).ifPresent(this::updateConfig);
        }
    }

    private void updateConfig(ConfigVariable configVariable) {
        log.info("Updating config for account limits ...");

        if(configVariables.containsKey(configVariable.getKey())){
            configVariables.put(configVariable.getKey(), configVariable.getValue());
        }
    }

    private int getConfigVarIntegerSafe(String configKey, int defaultValue) {
        try {
            return Integer.parseInt(configVariables.getOrDefault(configKey, String.valueOf(defaultValue)));
        } catch (NumberFormatException e) {
            log.error("Error parsing config var: {}, error message: {}", configKey, e.getMessage());
            return defaultValue;
        }
    }

    private void validateAdmin(User user, Account account) {
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, StandardRole.ROLE_SYSTEM_ADMIN);
        }
    }

    private void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private void storeAccountLogPostCommit(AccountLog accountLog) {
        AfterTxCommitTask task = () -> createAndStoreSingleAccountLog(accountLog);
        eventPublisher.publishEvent(task);
    }

    @Override
    public Account load(String accountUid) {
        return accountRepository.findOneByUid(accountUid);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberMembersLeftForGroup(final Group group, GroupJoinMethod joinMethod) {
        final boolean groupSizeLimited = Boolean.parseBoolean(configVariables.getOrDefault("group.size.limited","false"));
        final boolean groupJoinsLimited = Boolean.parseBoolean(configVariables.getOrDefault("group.joins.limited", "false"));

        final int freeGroupLimit = getConfigVarIntegerSafe("group.size.freemax", 300);

        log.info("Is group size limited? : {}, and joins? {}, and what is the free group limit? : {}, is group paid for? : {}",
                groupSizeLimited, groupJoinsLimited, freeGroupLimit, group.robustIsPaidFor());

        final boolean isGroupJoin = joinMethod != null && GroupJoinMethod.JOIN_CODE_METHODS.contains(joinMethod);
        final boolean limitDoesNotApply = !groupSizeLimited || (isGroupJoin && !groupJoinsLimited) || group.robustIsPaidFor();

        if (limitDoesNotApply) {
            return 99999;
        } else {
            int membershipCount = membershipRepository.countByGroup(group);
            return Math.max(0, freeGroupLimit - membershipCount);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public int numberTodosLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);

        int eventMonthlyLimitThreshold = Integer.parseInt(configVariables.getOrDefault("tasks.limit.threshold","10"), 10);

        boolean isSmallGroup = group.getMemberships().size() < eventMonthlyLimitThreshold;
        boolean isOnAccount = group.isPaidFor() && group.getAccount() != null && group.getAccount().isEnabled();

        if (isSmallGroup || isOnAccount)
            return LARGE_EVENT_LIMIT;

        int todosThisMonth = (int) todoRepository.count(Specification.where(hasGroupAsAncestor(group))
                .and(createdDateBetween(LocalDateTime.now().withDayOfMonth(1).withHour(0).toInstant(ZoneOffset.UTC), Instant.now())));

        int freeTodosPerMonth = getConfigVarIntegerSafe("tasks.monthly.free",4);
        return Math.max(0, freeTodosPerMonth - todosThisMonth);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberEventsLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        int eventMonthlyLimitThreshold = getConfigVarIntegerSafe("tasks.limit.threshold",10);
        boolean isSmallGroup = group.getMemberships().size() < eventMonthlyLimitThreshold;
        if (isSmallGroup || group.robustIsPaidFor()) {
            return LARGE_EVENT_LIMIT;
        } else {
            Instant startOfMonth = LocalDate.now().withDayOfMonth(1).atStartOfDay().toInstant(ZoneOffset.UTC);

            int eventsThisMonth = (int) eventRepository.count(Specification.where(EventSpecifications.hasGroupAsAncestor(group))
                    .and(EventSpecifications.createdDateTimeBetween(startOfMonth, Instant.now())));

            int freeEventsPerMonth = getConfigVarIntegerSafe("tasks.monthly.free",4);
            return Math.max(0, freeEventsPerMonth - eventsThisMonth);
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

        log.info("going to check for paid group on account = {}, for group = {}", account.getName(), group.getName());

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
            log.error("Conflict in group welcome messages ... check logs and debug");
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
    public void deactivateGroupWelcomes(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = group.getAccount();

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
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = group.getAccount();
        if (account == null) {
            return null;
        }

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
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = group.getAccount();
        if (account == null) {
            throw new IllegalArgumentException("Trying to create welcome notifications for non-account linked group");
        }

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
            log.info("sent {} logs and {} notifications for storage, exiting", bundle.getLogs().size(), bundle.getNotifications().size());
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
        final Account groupAccount = group.getAccount();

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
        log.debug("generating notifications for {} member", memberUids.size());
        Instant now = Instant.now().plus(templateStringIndex * WELCOME_MSG_INTERVAL, ChronoUnit.MILLIS);
        Set<Notification> notifications = userRepository.findByUidIn(memberUids).stream()
                .map(user -> fromTemplate(templateEntity, templateStringIndex, user.getMembership(group), accountLog, now))
                .collect(Collectors.toSet());
        log.debug("generated {} notifications", notifications.size());
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

    @Override
    public int numberGroupsAboveFreeLimit(int freeLimit){
        return this.groupRepository.countGroupsWhereSizeAboveLimit(freeLimit);
    }

    @Override
    public int numberGroupsBelowFreeLimit(int freeLimit){
        return this.groupRepository.countGroupsWhereSizeBelowLimit(freeLimit);
    }

    @Override
    public int getFreeGroupLimit() {
        return getConfigVarIntegerSafe("group.size.freemax", 300);
    }
}
