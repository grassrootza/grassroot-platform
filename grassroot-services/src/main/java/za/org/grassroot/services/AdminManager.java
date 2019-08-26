package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.ConfigVariable;
import za.org.grassroot.core.domain.GroupRole;
import za.org.grassroot.core.domain.StandardRole;
import za.org.grassroot.core.domain.User;
import za.org.grassroot.core.domain.UserLog;
import za.org.grassroot.core.domain.group.Group;
import za.org.grassroot.core.domain.group.GroupJoinMethod;
import za.org.grassroot.core.domain.group.GroupLog;
import za.org.grassroot.core.domain.group.GroupLog_;
import za.org.grassroot.core.domain.group.Group_;
import za.org.grassroot.core.domain.group.Membership;
import za.org.grassroot.core.domain.notification.SystemInfoNotification;
import za.org.grassroot.core.dto.membership.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.events.AlterConfigVariableEvent;
import za.org.grassroot.core.events.RemoveConfigVariableEvent;
import za.org.grassroot.core.repository.ConfigRepository;
import za.org.grassroot.core.repository.GroupLogRepository;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.MembershipRepository;
import za.org.grassroot.core.repository.UserLogRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.util.DateTimeUtil;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/02/04.
 */
@Service
public class AdminManager implements AdminService, ApplicationEventPublisherAware {

    private static final Logger logger = LoggerFactory.getLogger(AdminManager.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final GroupBroker groupBroker;
    private final GroupLogRepository groupLogRepository;
    private final UserLogRepository userLogRepository;
    private final PasswordEncoder passwordEncoder;
    private final MembershipRepository membershipRepository;

    private ConfigRepository configRepository;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    public AdminManager(UserRepository userRepository, GroupRepository groupRepository, GroupBroker groupBroker, GroupLogRepository groupLogRepository, 
                        UserLogRepository userLogRepository, MembershipRepository membershipRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.groupBroker = groupBroker;
        this.groupLogRepository = groupLogRepository;
        this.userLogRepository = userLogRepository;
        this.membershipRepository = membershipRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
    }

    @Autowired
    public void setConfigRepository(ConfigRepository configRepository) {
        this.configRepository = configRepository;
    }

    @Autowired
    public void setLogsAndNotificationsBroker(LogsAndNotificationsBroker logsAndNotificationsBroker) {
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
    }

    /**
     * SECTION: METHODS TO HANDLE GROUPS
     */

    @Override
    @Transactional
    public void updateGroupActive(String adminUserUid, String groupUid, boolean active) {
        validateAdminRole(adminUserUid);

        User user = userRepository.findOneByUid(adminUserUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setActive(active);

        groupLogRepository.save(new GroupLog(group, user, GroupLogType.GROUP_REMOVED,
                active ? "Activated by system admin" : "Deactivated by system admin"));
    }

    @Override
    @Transactional
    public void addMemberToGroup(String adminUserUid, String groupUid, MembershipInfo membershipInfo) {
        validateAdminRole(adminUserUid);
        groupBroker.addMembers(adminUserUid, groupUid, Collections.singleton(membershipInfo),
                GroupJoinMethod.ADDED_BY_SYS_ADMIN, true);
    }

    @Override
    @Transactional
    public void removeUserFromAllGroups(String adminUserUid, String userUid) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Set<Membership> memberships = user.getMemberships();
        logger.info("admin user now removing user from {} groups", memberships.size());
        for (Membership membership : memberships) {
            Group group = membership.getGroup();
            group.removeMembership(membership); // concurrency?
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Group> findGroupByJoinCode(final String adminUserUid, final String joinCode) {
        validateAdminRole(adminUserUid);
        return groupRepository.findOne(GroupSpecifications.hasJoinCode(joinCode));
    }

    @Override
    @Transactional
    public void removeJoinCodeFromGroup(final String adminUserUid, final String groupUid) {
        validateAdminRole(adminUserUid);
        final Group group = groupRepository.findOneByUid(groupUid);
        group.setGroupTokenCode(null);
        group.setTokenExpiryDateTime(Instant.now());
        // store a group log
    }

    @Override
    public void grantJoinCodeToGroup(final String adminUserUid, final String groupUid, final String joinCode) {
        validateAdminRole(adminUserUid);
        final Group group = groupRepository.findOneByUid(groupUid);
        group.setGroupTokenCode(joinCode);
        group.setTokenExpiryDateTime(DateTimeUtil.getVeryLongAwayInstant());
        // store a group log
    }


    @Override
    @Transactional
    public void updateUserPassword(String adminUserUid, String userUid, String newPassword) {
        Objects.requireNonNull(adminUserUid);
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(newPassword);

        validateAdminRole(adminUserUid);

        User user = userRepository.findOneByUid(userUid);
        String encodedPassword = passwordEncoder.encode(newPassword);
        user.setPassword(encodedPassword);

        userLogRepository.save(new UserLog(user.getUid(), UserLogType.ADMIN_CHANGED_PASSWORD,
                adminUserUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional
    public void updateConfigVariable(String key, String newValue,String description) {
        ConfigVariable var = configRepository.findOneByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Error! Trying to update non-existent var"));
        var.setValue(newValue);
        var.setDescription(description);

        configRepository.save(var);

        AlterConfigVariableEvent alterConfigVariableEvent = new AlterConfigVariableEvent(this,var.getKey(),false);
        this.applicationEventPublisher.publishEvent(alterConfigVariableEvent);

        logger.info("Updated variable, exiting");
    }

    @Override
    @Transactional
    public void createConfigVariable(String key, String value,String description) {
        Optional<ConfigVariable> existing = configRepository.findOneByKey(key);
        if (existing.isPresent())
            throw new IllegalArgumentException("Trying to create variable with existing key name");

        ConfigVariable newVar = new ConfigVariable(key, value,description);

        configRepository.save(newVar);

        AlterConfigVariableEvent alterConfigVariableEvent = new AlterConfigVariableEvent(this,newVar.getKey(),true);
        this.applicationEventPublisher.publishEvent(alterConfigVariableEvent);

        logger.info("Created new variable, exiting");
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getCurrentConfigVariables() {
        List<ConfigVariable> rawVariables = configRepository.findAll();
        logger.info("Found {} raw config variables", rawVariables.size());
        Map<String, String> configVars = rawVariables.stream()
                .collect(Collectors.toMap(ConfigVariable::getKey, ConfigVariable::getValue));
        logger.info("And mapped them to: {}", configVars);
        return configVars;
    }

    @Override
    @Transactional
    public List<ConfigVariable> getAllConfigVariables(){
        return configRepository.findAll();
    }

    @Override
    @Transactional
    public void deleteConfigVariable(String key) {
        configRepository.findOneByKey(key).ifPresent(configVar -> {
            configRepository.delete(configVar);
            RemoveConfigVariableEvent removeConfigVariableEvent = new RemoveConfigVariableEvent(this,"DELETE IT <<<<<>>>>>");
            this.applicationEventPublisher.publishEvent(removeConfigVariableEvent);
        });
    }

    @Override
    @Transactional
    public int freeUpInactiveJoinTokens() {
        // this is a pretty heavy activity, so don't do it often, and can't really think of a better way
        Instant oneYearAgo = Instant.now().minus(365, ChronoUnit.DAYS);
        Specification<GroupLog> joinCodeUseInLastYear = (root, query, cb) -> cb.and(
                cb.greaterThan(root.get(GroupLog_.createdDateTime), oneYearAgo),
                cb.equal(root.get(GroupLog_.groupLogType), GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE));
        List<GroupLog> groupLogs = groupLogRepository.findAll(joinCodeUseInLastYear);
        Set<Long> groupLogIds = groupLogs.stream().map(log -> log.getGroup().getId()).collect(Collectors.toSet());
        logger.info("retrieved {} group log IDs that used token in last year", groupLogIds.size());
        Specification<Group> hasJoinCodeButNotIn = (root, query, cb) -> cb.and(
                cb.not(root.get(Group_.id).in(groupLogIds)),
                cb.isNotNull(root.get(Group_.groupTokenCode)),
                cb.greaterThan(root.get(Group_.tokenExpiryDateTime), Instant.now())
        );
        List<Group> groupsThatCanRecycleCode = groupRepository.findAll(hasJoinCodeButNotIn);
        int numberOfGroups = groupsThatCanRecycleCode.size();
        logger.info("found {} groups that can recycle their join code", numberOfGroups);
        // then we'll invalidate and notify user (but first, let's see how many)
        return numberOfGroups;
    }

    // note: also going to need logs and notification, but this is roughly the way.
    @Override
    @Transactional
    public int broadcastMessageToActiveGroupOrganizers(String adminUserUid, String message, Instant lastActiveThreshold, boolean dryRun) {
        long startTime = System.currentTimeMillis();
        logger.info("Initiating large scale broadcast, with message: ", message);
        validateAdminRole(adminUserUid);
        Specification<Group> stillActive = (root, query, cb) -> cb.isTrue(root.get(Group_.active));
        Specification<Group> activeSinceTime = (root, query, cb) -> cb.or(
            cb.greaterThan(root.get(Group_.lastTaskCreationTime), lastActiveThreshold), 
            cb.greaterThan(root.get(Group_.lastGroupChangeTime), lastActiveThreshold));
        List<Group> activeGroups = groupRepository.findAll(stillActive.and(activeSinceTime));
        List<Membership> groupOrganizerMemberships = membershipRepository.findByGroupInAndRole(activeGroups, GroupRole.ROLE_GROUP_ORGANIZER);
        Set<User> organizerUsers = groupOrganizerMemberships.stream().map(Membership::getUser).collect(Collectors.toSet());
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        organizerUsers.forEach((user) -> {
            UserLog userLog = new UserLog(user.getUid(), UserLogType.NOTIFIED_LANGUAGES, "Sent message to all group organizers", UserInterfaceType.SYSTEM);
            SystemInfoNotification notification = new SystemInfoNotification(user, message, userLog);
            bundle.addLog(userLog);
            bundle.addNotification(notification);
        });
        logger.info("Would be about to send {} notifications, took {} msecs", organizerUsers.size(), System.currentTimeMillis() - startTime);
        if (!dryRun) {
            logsAndNotificationsBroker.storeBundle(bundle);
        } else {
            logger.info("Dry run, so sending only to user admin");
            User thisUser = userRepository.findOneByUid(adminUserUid);
            UserLog adminLog = new UserLog(adminUserUid, UserLogType.NOTIFIED_LANGUAGES, "Sent dry run message to user", UserInterfaceType.SYSTEM);
            SystemInfoNotification notification = new SystemInfoNotification(thisUser, message, adminLog);
            logsAndNotificationsBroker.storeBundle(new LogsAndNotificationsBundle(Collections.singleton(adminLog), Collections.singleton(notification)));
        }
        return bundle.getNotifications().size();
    }

    private void validateAdminRole(String adminUserUid) {
        User admin = userRepository.findOneByUid(adminUserUid);
        if (!admin.getStandardRoles().contains(StandardRole.ROLE_SYSTEM_ADMIN)) {
            throw new AccessDeniedException("Error! User does not have admin role");
        }
    }

}
