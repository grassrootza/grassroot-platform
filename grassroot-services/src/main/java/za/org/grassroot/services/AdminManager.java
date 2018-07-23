package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.group.*;
import za.org.grassroot.core.domain.notification.SystemInfoNotification;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserInterfaceType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.UserSpecifications;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.graph.GraphBroker;
import za.org.grassroot.services.group.GroupBroker;
import za.org.grassroot.services.task.TaskBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Created by luke on 2016/02/04.
 */
@Service
public class AdminManager implements AdminService {

    private static final Logger logger = LoggerFactory.getLogger(AdminManager.class);

    private final UserRepository userRepository;
    private final GroupRepository groupRepository;
    private final RoleRepository roleRepository;
    private final MembershipRepository membershipRepository;
    private final GroupBroker groupBroker;
    private final GroupLogRepository groupLogRepository;
    private final UserLogRepository userLogRepository;
    private final PasswordEncoder passwordEncoder;

    private GraphBroker graphBroker;
    private TaskBroker taskBroker;
    private LogsAndNotificationsBroker logsAndNotificationsBroker;

    @Autowired
    public AdminManager(UserRepository userRepository, GroupRepository groupRepository, RoleRepository roleRepository, MembershipRepository membershipRepository, GroupBroker groupBroker, GroupLogRepository groupLogRepository, UserLogRepository userLogRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.groupRepository = groupRepository;
        this.roleRepository = roleRepository;
        this.membershipRepository = membershipRepository;
        this.groupBroker = groupBroker;
        this.groupLogRepository = groupLogRepository;
        this.userLogRepository = userLogRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Autowired(required = false)
    public void setGraphBroker(GraphBroker graphBroker) {
        this.graphBroker = graphBroker;
    }

    @Autowired(required = false)
    public void setTaskBroker(TaskBroker taskBroker) {
        this.taskBroker = taskBroker;
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
    @Transactional
    public void addSystemRole(String adminUserUid, String userUid, String systemRole) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Role role = roleRepository.findByName(BaseRoles.ROLE_ALPHA_TESTER).get(0);
        user.addStandardRole(role);
        userLogRepository.save(new UserLog(userUid, UserLogType.GRANTED_SYSTEM_ROLE,
                systemRole + " granted by admin. uid::" + adminUserUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional
    public void removeStdRole(String adminUserUid, String userUid, String systemRole) {
        validateAdminRole(adminUserUid);
        User user = userRepository.findOneByUid(userUid);
        Role role = roleRepository.findByNameAndRoleType(systemRole, Role.RoleType.STANDARD).get(0);
        logger.info("found a role? : {}, and a user : {}", role, user);
        user.removeStandardRole(role);
        userLogRepository.save(new UserLog(userUid, UserLogType.REVOKED_SYSTEM_ROLE,
                systemRole + " removed by admin. uid::" + userUid, UserInterfaceType.WEB));
    }

    @Override
    @Transactional
    public String createUserWithSystemRole(String adminUserUid, String displayName, String phoneNumber, String emailAddress, String systemRole) {
        validateAdminRole(adminUserUid);
        if (StringUtils.isEmpty(phoneNumber) && StringUtils.isEmpty(emailAddress)) {
            throw new IllegalArgumentException("Error! One of email or phone number must be non-empty");
        }
        String msisdn = StringUtils.isEmpty(phoneNumber) ? null : PhoneNumberUtil.convertPhoneNumber(phoneNumber);
        User user = msisdn != null && userRepository.existsByPhoneNumber(msisdn) ? userRepository.findByPhoneNumberAndPhoneNumberNotNull(msisdn) :
                userRepository.findByEmailAddressAndEmailAddressNotNull(emailAddress);
        if (user == null) {
            user = new User(msisdn, displayName, emailAddress);
            userRepository.saveAndFlush(user);
            userLogRepository.save(new UserLog(user.getUid(), UserLogType.CREATED_IN_DB,
                    "created by admin, uid : " + adminUserUid, UserInterfaceType.WEB));
        } else {
            if (!StringUtils.isEmpty(displayName)) {
                user.setDisplayName(displayName);
            }
            if (!StringUtils.isEmpty(msisdn)) {
                user.setPhoneNumber(msisdn);
            }
            if (!StringUtils.isEmpty(emailAddress)) {
                user.setEmailAddress(emailAddress);
            }
        }
        addSystemRole(adminUserUid, user.getUid(), systemRole);
        return user.getUid();
    }

    @Override
    @Transactional(readOnly = true)
    public List<User> getUsersWithStdRole(String adminUserUid, String systemRole) {
        validateAdminRole(adminUserUid);
        return userRepository.findAll(UserSpecifications.hasStandardRole(systemRole));
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
    public long sendBatchOfAndroidLinks(String adminUserUid, int batchSize) {
        validateAdminRole(Objects.requireNonNull(adminUserUid));

        Specification<UserLog> hasBeenSentLink = (root, query, cb) -> cb.equal(root.get(UserLog_.userLogType),
                UserLogType.RECEIVED_ANDROID_BROADCAST);
        Set<String> alreadySentUids = userLogRepository.findAll(hasBeenSentLink).stream()
                .map(UserLog::getUserUid).collect(Collectors.toSet());

        PageRequest pageRequest = PageRequest.of(0, batchSize);
        Specification<User> hasInitiatedSession =  (root, query, cb) -> cb.isTrue(root.get(User_.hasInitiatedSession));
        Specification<User> notOnAndroid = (root, query, cb) -> cb.isFalse(root.get(User_.hasAndroidProfile));
        Specification<User> notWithUidIn = (root, query, cb) -> cb.not(root.get(User_.uid).in(alreadySentUids));
        Specification<User> hasPhoneNumber = (root, query, cb) -> cb.isNotNull(root.get(User_.phoneNumber));

        Page<User> usersToReceive = userRepository.findAll(hasInitiatedSession.and(notOnAndroid).and(hasPhoneNumber)
                .and(notWithUidIn), pageRequest);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        usersToReceive.forEach(user -> {
            UserLog log = new UserLog(user.getUid(), UserLogType.RECEIVED_ANDROID_BROADCAST, null, UserInterfaceType.SYSTEM);
            SystemInfoNotification notification = new SystemInfoNotification(user, "We have Android! Link: ", log);
            bundle.addLog(log);
            bundle.addNotification(notification);
        });

        logger.info("Done processing, found {} users, storing {} notifications and logs", usersToReceive.getSize(), bundle.getNotifications().size());
        logsAndNotificationsBroker.storeBundle(bundle);

        return usersToReceive.getTotalElements();

    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void populateGrassrootGraphTasks(String userUid) {
        DebugUtil.transactionRequired("");
        if (graphBroker != null) {
            DebugUtil.transactionRequired("");
            taskBroker.loadAllTasks().forEach(task -> graphBroker.addTaskToGraph(task.getUid(), task.getTaskType(),
                    taskBroker.fetchUserUidsForTask(userUid, task.getUid(), task.getTaskType())));
        }
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void populateGraphUserAnnotations() {
        if (graphBroker != null) {
            Specification<User> spec = UserSpecifications.hasInitiatedSession().and(UserSpecifications.isEnabled());
            userRepository.findAll(spec).forEach(user -> graphBroker.annotateUser(user.getUid(), null, null, true));
        }
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void populateGraphGroupAnnotations() {
        if (graphBroker != null) {
            Specification<Group> groups = Specification.where((root, query, cb) -> cb.isTrue(root.get(Group_.active)));
            groupRepository.findAll(groups).forEach(group -> graphBroker.annotateGroup(group.getUid(), null, null, true));
        }
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void populateGraphMembershipAnnotations() {
        if (graphBroker != null) {
            membershipRepository.findByGroupActiveTrue().forEach(membership ->
                    graphBroker.annotateMembership(membership.getUser().getUid(), membership.getGroup().getUid(), null, true));
        }
    }

    @Async
    @Override
    @Transactional(readOnly = true)
    public void populateGraphTaskAnnotations() {
        DebugUtil.transactionRequired("");
        if (graphBroker != null) {
            DebugUtil.transactionRequired("");
            taskBroker.loadAllTasks().forEach(task -> graphBroker.annotateTask(task.getUid(), task.getTaskType(), null, null, true));
        }
    }

    private void validateAdminRole(String adminUserUid) {
        User admin = userRepository.findOneByUid(adminUserUid);
        Role adminRole = roleRepository.findByName(BaseRoles.ROLE_SYSTEM_ADMIN).get(0);
        if (!admin.getStandardRoles().contains(adminRole)) {
            throw new AccessDeniedException("Error! User does not have admin role");
        }
    }

}
