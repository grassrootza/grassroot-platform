package za.org.grassroot.services.account;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEvent;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationListener;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.BaseRoles;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.Notification;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.account.AccountLog;
import za.org.grassroot.core.domain.broadcast.Broadcast;
import za.org.grassroot.core.domain.broadcast.BroadcastSchedule;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.GroupWelcomeNotification;
import za.org.grassroot.core.domain.task.Event;
import za.org.grassroot.core.enums.AccountLogType;
import za.org.grassroot.core.events.CreateConfigVariableEvent;
import za.org.grassroot.core.events.UpdateConfigVariableEvent;
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
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.specifications.TodoSpecifications.createdDateBetween;
import static za.org.grassroot.core.specifications.TodoSpecifications.hasGroupAsAncestor;

/**
 * Created by luke on 2016/10/25.
 */
@Service @Slf4j
public class AccountFeaturesBrokerImpl implements AccountFeaturesBroker, ApplicationListener {

    private boolean GROUP_SIZE_LIMITED = false;
    private int FREE_GROUP_LIMIT = 300;
    private int FREE_TODOS_PER_MONTH = 4;
    private int FREE_EVENTS_PER_MONTH = 4;

    private int eventMonthlyLimitThreshold = 10;
    private String eventLimitStartString = "2017-04-01";
    private Instant eventLimitStart;

    private static final int LARGE_EVENT_LIMIT = 99;
    private static final long WELCOME_MSG_INTERVAL = 60 * 1000; // 1 minute

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final PermissionBroker permissionBroker;
    private final TodoRepository todoRepository;
    private final EventRepository eventRepository;

    private final AccountRepository accountRepository;
    private final BroadcastRepository templateRepository;
    private final MessageAssemblingService messageAssemblingService;
    private final ConfigRepository configRepository;

    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    public AccountFeaturesBrokerImpl(UserRepository userRepository, GroupRepository groupRepository, TodoRepository todoRepository,
                                     EventRepository eventRepository, PermissionBroker permissionBroker, AccountRepository accountRepository,
                                     BroadcastRepository templateRepository, MessageAssemblingService messageAssemblingService,
                                     LogsAndNotificationsBroker logsAndNotificationsBroker, ConfigRepository configRepository) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
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
        setEventLimitStart();
    }

    // run this once per hour
    @Scheduled(fixedRate = 60 * 60 * 1000)
    public void updateConfig() {
        log.info("Updating config for account limits ...");

        Map<String, String> configVars = configRepository.findAll().stream()
                .collect(Collectors.toMap(ConfigVariable::getKey, ConfigVariable::getValue));

        log.info("Current config variables {}",configVars);

        GROUP_SIZE_LIMITED = Boolean.parseBoolean(configVars.getOrDefault("groups.size.limit", "false"));
        FREE_GROUP_LIMIT = Integer.parseInt(configVars.getOrDefault("groups.size.freemax", "300"));
        FREE_TODOS_PER_MONTH = Integer.parseInt(configVars.getOrDefault("todos.monthly.free", "4"));
        FREE_EVENTS_PER_MONTH = Integer.parseInt(configVars.getOrDefault("accounts.events.monthly.free", "4"));
        eventMonthlyLimitThreshold = Integer.parseInt(configVars.getOrDefault("events.limit.threshold", "10"));
        eventLimitStartString = configVars.getOrDefault("grassroot.events.limit.started", "2017-04-01");
        setEventLimitStart();

        log.info("Free todos per month default to ={}",FREE_TODOS_PER_MONTH);
    }

    private void setEventLimitStart() {
        LocalDate ldEventLimitStart;
        try {
            ldEventLimitStart = LocalDate.parse(eventLimitStartString, DateTimeFormatter.ISO_LOCAL_DATE);
        } catch (DateTimeParseException e) {
            log.error("Error parsing! {}", e);
            ldEventLimitStart = LocalDate.of(2017, 4, 1);
        }
        eventLimitStart = ldEventLimitStart.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    private void validateAdmin(User user, Account account) {
        if (!account.getAdministrators().contains(user)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }
    }

    protected void createAndStoreSingleAccountLog(AccountLog accountLog) {
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(accountLog);
        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    protected void storeAccountLogPostCommit(AccountLog accountLog) {
        AfterTxCommitTask task = () -> createAndStoreSingleAccountLog(accountLog);
        eventPublisher.publishEvent(task);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberMembersLeftForGroup(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        int currentMembers = group.getMemberships().size();
        return !GROUP_SIZE_LIMITED || group.robustIsPaidFor() ? 99999 : Math.max(0, FREE_GROUP_LIMIT - currentMembers);
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
        if (isSmallGroup || group.robustIsPaidFor()) {
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
    public void updateGroupWelcomeNotifications(String userUid, String groupUid, List<String> messages, Duration delayToSend) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(messages);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Account account = group.getAccount();

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
                .map(user -> fromTemplate(templateEntity, templateStringIndex, group.getMembership(user), accountLog, now))
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
    public void onApplicationEvent(ApplicationEvent event) {
        if(event instanceof UpdateConfigVariableEvent){
            ConfigVariable configVariable = configRepository.findOneByKey(((UpdateConfigVariableEvent) event).getKey());
            log.info("Config variable updated, key ={}, value ={}",configVariable.getKey(),configVariable.getValue());
            updateConfig();
        }

        if(event instanceof CreateConfigVariableEvent) {
            log.info("Config variable created ...........");
            updateConfig();
        }
    }
}
