package za.org.grassroot.services.group;

import lombok.Setter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.domain.Specifications;
import org.springframework.scheduling.annotation.Async;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.account.Account;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.domain.notification.JoinCodeNotification;
import za.org.grassroot.core.domain.task.Meeting;
import za.org.grassroot.core.dto.MembershipInfo;
import za.org.grassroot.core.enums.*;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.core.specifications.GroupSpecifications;
import za.org.grassroot.core.specifications.MembershipSpecifications;
import za.org.grassroot.core.util.AfterTxCommitTask;
import za.org.grassroot.core.util.DebugUtil;
import za.org.grassroot.core.util.InvalidPhoneNumberException;
import za.org.grassroot.core.util.PhoneNumberUtil;
import za.org.grassroot.integration.UrlShortener;
import za.org.grassroot.services.MessageAssemblingService;
import za.org.grassroot.services.PermissionBroker;
import za.org.grassroot.services.account.AccountGroupBroker;
import za.org.grassroot.services.exception.*;
import za.org.grassroot.services.user.GcmRegistrationBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;
import za.org.grassroot.services.util.TokenGeneratorService;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;
import static za.org.grassroot.core.enums.UserInterfaceType.UNKNOWN;
import static za.org.grassroot.core.util.DateTimeUtil.*;

@Service
public class GroupBrokerImpl implements GroupBroker, ApplicationContextAware {

    private final Logger logger = LoggerFactory.getLogger(GroupBrokerImpl.class);

    @Value("${grassroot.groups.size.limit:false}")
    private boolean limitGroupSize;
    @Value("${grassroot.groups.size.freemax:300}")
    private int freeGroupSizeLimit;
    @Value("${grassroot.groups.join.words.max:3}")
    private int maxJoinWords;

    private final Environment environment;

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final MembershipRepository membershipRepository;
    private final GroupLogRepository groupLogRepository;
    private final GroupJoinCodeRepository groupJoinCodeRepository;

    private final PermissionBroker permissionBroker;
    private final ApplicationEventPublisher applicationEventPublisher;
    private final LogsAndNotificationsBroker logsAndNotificationsBroker;
    private final TokenGeneratorService tokenGeneratorService;
    private final MessageAssemblingService messageAssemblingService;

    private final int GROUPS_LIMIT = 12;

    private GcmRegistrationBroker gcmRegistrationBroker;
    private final AccountGroupBroker accountGroupBroker;
    private final UrlShortener urlShortener;

    @Setter private ApplicationContext applicationContext;

    //self injection, required to run it's own transactional method after another transaction completes
//    @Autowired
//    private GroupBroker groupBroker;

    @Autowired
    public GroupBrokerImpl(GroupRepository groupRepository, Environment environment, UserRepository userRepository,
                           MembershipRepository membershipRepository, GroupLogRepository groupLogRepository, GroupJoinCodeRepository groupJoinCodeRepository, PermissionBroker permissionBroker,
                           ApplicationEventPublisher applicationEventPublisher, LogsAndNotificationsBroker logsAndNotificationsBroker,
                           TokenGeneratorService tokenGeneratorService, MessageAssemblingService messageAssemblingService,
                           AccountGroupBroker accountGroupBroker, UrlShortener urlShortener) {
        this.groupRepository = groupRepository;
        this.environment = environment;
        this.userRepository = userRepository;
        this.membershipRepository = membershipRepository;
        this.groupLogRepository = groupLogRepository;
        this.groupJoinCodeRepository = groupJoinCodeRepository;
        this.permissionBroker = permissionBroker;
        this.applicationEventPublisher = applicationEventPublisher;
        this.logsAndNotificationsBroker = logsAndNotificationsBroker;
        this.tokenGeneratorService = tokenGeneratorService;
        this.messageAssemblingService = messageAssemblingService;
        this.accountGroupBroker = accountGroupBroker;
        this.urlShortener = urlShortener;
    }

    @Autowired(required = false)
    public void setGcmRegistrationBroker(GcmRegistrationBroker gcmRegistrationBroker) {
        this.gcmRegistrationBroker = gcmRegistrationBroker;
    }

    @Override
    @Transactional(readOnly = true)
    public Group load(String groupUid) {
        return groupRepository.findOneByUid(groupUid);
    }

    @Override
    @Transactional(readOnly = true)
    public Group checkForDuplicate(String userUid, String groupName) {
        // checks if a group with the same name has been created by the same user in the last 10 minutes
        User user = userRepository.findOneByUid(userUid);
        return groupRepository.findFirstByCreatedByUserAndGroupNameAndCreatedDateTimeAfterAndActiveTrue(user, groupName,
                Instant.now().minus(20, ChronoUnit.MINUTES));
    }

    @Override
    @Transactional
    public Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
                        GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes, boolean openJoinToken, boolean discoverable) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(membershipInfos);
        Objects.requireNonNull(groupPermissionTemplate);

        User user = userRepository.findOneByUid(userUid);

        Group parent = null;
        if (parentGroupUid != null) {
            parent = groupRepository.findOneByUid(parentGroupUid);
        }

        logger.info("Creating new group: name={}, description={}, membershipInfos={}, groupPermissionTemplate={},  parent={}, user={}, openJoinToken=",
                name, description, membershipInfos, groupPermissionTemplate, parent, user, openJoinToken);

        Group group = new Group(name, user);
        // last: set some advanced features, with defaults in case null passed
        group.setDescription((description == null) ? "" : description);
        group.setReminderMinutes((reminderMinutes == null) ? (24 * 60) : reminderMinutes);
        group.setParent(parent);
        group.setDiscoverable(discoverable);

        LogsAndNotificationsBundle bundle = addMemberships(user, group, membershipInfos,
                GroupJoinMethod.ADDED_AT_CREATION, user.getName(), true, true);

        bundle.addLog(new GroupLog(group, user, GroupLogType.GROUP_ADDED, null));
        if (parent != null) {
            bundle.addLog(new GroupLog(parent, user, GroupLogType.SUBGROUP_ADDED, null, group, null, "Subgroup added"));
        }

        permissionBroker.setRolePermissionsFromTemplate(group, groupPermissionTemplate);
        group = groupRepository.save(group);

        logger.info("Group created under UID {}", group.getUid());

        if (openJoinToken) {
            JoinTokenOpeningResult joinTokenOpeningResult = openJoinTokenInternal(user, group, null);
            bundle.addLog(joinTokenOpeningResult.getGroupLog());
        }

        storeBundleAfterCommit(bundle);

        return group;
    }

    private void logActionLogsAfterCommit(Set<ActionLog> actionLogs) {
        if (!actionLogs.isEmpty()) {
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addLogs(actionLogs);
            storeBundleAfterCommit(bundle);
        }
    }

    private void storeBundleAfterCommit(LogsAndNotificationsBundle bundle) {
        AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle); // we want to log group events after transaction has committed
        applicationEventPublisher.publishEvent(afterTxCommitTask);
    }

    @Override
    @Transactional
    public void deactivate(String userUid, String groupUid, boolean checkIfWithinTimeWindow) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!isDeactivationAvailable(user, group, checkIfWithinTimeWindow)) {
            throw new GroupDeactivationNotAvailableException();
        }

        logger.info("Deactivating group: {}", group);
        group.setActive(false);

        Set<ActionLog> actionLogs = new HashSet<>();
        actionLogs.add(new GroupLog(group, user, GroupLogType.GROUP_REMOVED, null));
        if (group.getParent() != null) {
            actionLogs.add(new GroupLog(group.getParent(), user, GroupLogType.SUBGROUP_REMOVED, null, group, null, null));
        }

        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow) {
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        boolean isUserGroupCreator = group.getCreatedByUser().equals(user);
        if (!checkIfWithinTimeWindow) {
            return isUserGroupCreator;
        } else {
            Integer timeWindow = environment.getProperty("grassroot.groups.delete.window", Integer.class);
            Instant deactivationTimeThreshold = group.getCreatedDateTime().plus(Duration.ofHours(timeWindow == null ? 48 : timeWindow));
            boolean isGroupMalformed = (group.getGroupName() == null || group.getGroupName().length() < 2)
		            && group.getMembers().size() <= 2;
	        return isUserGroupCreator && (isGroupMalformed || Instant.now().isBefore(deactivationTimeThreshold));
        }
    }

    @Override
    @Transactional
    public void updateName(String userUid, String groupUid, String name) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(name);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setGroupName(name);

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_RENAMED, null, null, null, group.getGroupName());
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional
    public void updateDescription(String userUid, String groupUid, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(description);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setDescription(description);
        GroupLog groupLog = new GroupLog(group, user, GroupLogType.DESCRIPTION_CHANGED, null, null, null,
                "Group description changed to " + group.getDescription());
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos, GroupJoinMethod joinMethod, boolean adminUserCalling) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!adminUserCalling) {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
            if (!checkGroupSizeLimit(group, membershipInfos.size())) {
                throw new GroupSizeLimitExceededException();
            }
        } else {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        logger.info("Adding members: group={}, memberships={}, user={}", group, membershipInfos, user);
        try {
            LogsAndNotificationsBundle bundle = addMemberships(user, group, membershipInfos, joinMethod, user.getName(), false, true);
            storeBundleAfterCommit(bundle);
        } catch (InvalidPhoneNumberException e) {
            logger.error("Error! Invalid phone number : " + e.getMessage());
        }
    }

    @Override
    @Transactional
    public void copyMembersIntoGroup(String userUid, String groupUid, Set<String> memberUids) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);

        if (!checkGroupSizeLimit(group, memberUids.size())) {
            throw new GroupSizeLimitExceededException();
        }

        List<User> users = userRepository.findByUidIn(memberUids);
        Set<User> userSet = new HashSet<>(users);
        logger.info("list size {}, set size {}", users.size(), userSet.size());
        group.addMembers(userSet, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.COPIED_INTO_GROUP, null);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        // recursively add users to all parent groups
        Group parentGroup = group.getParent();
        while (parentGroup != null) {
            parentGroup.addMembers(userSet, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.COPIED_INTO_GROUP, null);
            createSubGroupAddedLogs(parentGroup, group, user, users, bundle);
            parentGroup = parentGroup.getParent();
        }

        for (User u  : users) {
            GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_MEMBER_ADDED, u, null, null, null);
            bundle.addLog(groupLog);
            notifyNewMembersOfUpcomingMeetings(bundle, u, group, groupLog);
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    private void createSubGroupAddedLogs(Group parent, Group child, User initiator, List<User> users, LogsAndNotificationsBundle bundle) {
        users.forEach(u -> bundle.addLog(new GroupLog(parent, initiator, GroupLogType.GROUP_MEMBER_ADDED_INTO_SUBGROUP_OTHER,
                u, child, null, null)));
    }

    @Override
    @Transactional
    public void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed, UserInterfaceType interfaceType) {
        User user = userRepository.findOneByUid(userUidToAdd);
        Group group = groupRepository.findOneByUid(groupUid);

        validateJoinCode(group, tokenPassed);
        recordJoinCodeInbound(group, tokenPassed);

        selfJoinViaCode(user, group, getJoinMethodFromInterface(interfaceType), tokenPassed, null, null);
    }

    @Override
    @Transactional
    public String addMemberViaJoinPage(String groupUid, String code, String userUid, String name, String phone,
                                       String email, Province province, List<String> topics, UserInterfaceType interfaceType) {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(code);
        if (StringUtils.isEmpty(userUid) && StringUtils.isEmpty(phone) && StringUtils.isEmpty(email)) {
            throw new IllegalArgumentException("Error! At least one out of user Id, phone or email must be non-empty");
        }

        Group group = groupRepository.findOneByUid(groupUid);
        validateJoinCode(group, code); // don't record use, as already done elsewhere

        User joiningUser = null;
        boolean userExists;
        if (!StringUtils.isEmpty(userUid)) {
            joiningUser = userRepository.findOneByUid(userUid);
            userExists = true;
        } else {
            String msisdn = StringUtils.isEmpty(phone) ? null : PhoneNumberUtil.convertPhoneNumber(phone);
            if (msisdn != null)
                joiningUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull(msisdn);

            if (joiningUser == null && !StringUtils.isEmpty(email))
                joiningUser = userRepository.findByEmailAddressAndEmailAddressNotNull(email);

            userExists = joiningUser != null;
            if (!userExists) {
                // have to do these as otherwise an empty string might trigger uniqueness constraint
                joiningUser = new User(StringUtils.isEmpty(msisdn) ? null : msisdn, name, StringUtils.isEmpty(email) ? null : email);
                userRepository.saveAndFlush(joiningUser);
            }
        }

        // note: we do _not_ override email or phone otherwise it would create a security vulnerability
        boolean updatedUserDetails = false;
        if (province != null) {
            joiningUser.setProvince(province);
            updatedUserDetails = userExists;
        }

        if (!StringUtils.isEmpty(name)) {
            joiningUser.setDisplayName(name);
            updatedUserDetails = userExists;
        }

        Set<UserLog> userLogs = new HashSet<>();
        userLogs.add(new UserLog(joiningUser.getUid(), UserLogType.USED_A_JOIN_CODE, group.getUid(), interfaceType));
        if (updatedUserDetails) {
            userLogs.add(new UserLog(joiningUser.getUid(), UserLogType.DETAILS_CHANGED_ON_JOIN,
                    String.format("name: %s, province: %s", name, province), interfaceType));
        }

        selfJoinViaCode(joiningUser, group, GroupJoinMethod.URL_JOIN_WORD, code, topics, userLogs);
        return joiningUser.getUid();
    }

    private void selfJoinViaCode(User user, Group group, GroupJoinMethod joinMethod, String code, List<String> topics, Set<UserLog> userLogs) {
        logger.info("Adding a member via token code: code={}, group={}, user={}", code, group, user);
        Membership membership = group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, joinMethod, code);
        if (topics != null) {
            membership.setTopics(new HashSet<>(topics));
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        // recursively add user to all parent groups
        Group parentGroup = group.getParent();
        while (parentGroup != null) {
            parentGroup.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.SELF_JOINED, code);
            bundle.addLog(new GroupLog(parentGroup, user, GroupLogType.GROUP_MEMBER_ADDED_VIA_SUBGROUP_CODE, user, group, null, null));
            parentGroup = parentGroup.getParent();
        }

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
                user, null, null, "Member joined via join code: " + code);
        bundle.addLog(groupLog);
        if (userLogs != null && !userLogs.isEmpty()) {
            bundle.addLogs(userLogs.stream().map(u -> (ActionLog) u).collect(Collectors.toSet()));
        }
        notifyNewMembersOfUpcomingMeetings(bundle, user, group, groupLog);
        storeBundleAfterCommit(bundle);
    }

    private GroupJoinMethod getJoinMethodFromInterface(UserInterfaceType interfaceType) {
        logger.info("returning join method from interface: {}", interfaceType);
        switch (interfaceType) {
            case UNKNOWN:
                return GroupJoinMethod.JOIN_CODE_OTHER;
            case USSD:
                return GroupJoinMethod.USSD_JOIN_CODE;
            case WEB:
                return GroupJoinMethod.SEARCH_JOIN_WORD;
            case ANDROID:
                return GroupJoinMethod.SEARCH_JOIN_WORD;
            case SYSTEM:
                return GroupJoinMethod.JOIN_CODE_OTHER;
            case INCOMING_SMS:
                return GroupJoinMethod.SMS_JOIN_WORD;
            case WEB_2:
                return GroupJoinMethod.URL_JOIN_WORD;
            case ANDROID_2:
                return GroupJoinMethod.SEARCH_JOIN_WORD;
            default:
                return GroupJoinMethod.JOIN_CODE_OTHER;
        }
    }


    private void validateJoinCode(Group group, String joinCode) {
        Set<String> joinWords = groupJoinCodeRepository.selectActiveJoinCodesForGroup(group);
        if (!joinWords.contains(joinCode.toLowerCase()) && !joinCode.equals(group.getGroupTokenCode()) || Instant.now().isAfter(group.getTokenExpiryDateTime()))
            throw new InvalidTokenException("Invalid token: " + joinCode);
    }

    private void recordJoinCodeInbound(Group group, String code) {
        DebugUtil.transactionRequired("");
        GroupJoinCode gjc = groupJoinCodeRepository.findByGroupUidAndCodeAndActiveTrue(group.getUid(), code);
        if (gjc != null) {
            gjc.incrementInboundUses();
        }
    }

    // todo : make this actually generate and send notifications
    @Override
    @Transactional(readOnly = true)
    public void notifyOrganizersOfJoinCodeUse(Instant periodStart, Instant periodEnd) {
        logger.info("Checking whether we need to notify any organizers of join code use ...");
        List<Group> groupsWhereJoinCodeUsed = groupRepository.findGroupsWhereJoinCodeUsedBetween(periodStart, periodEnd);
        logger.info("What the repository returned: {}", groupsWhereJoinCodeUsed != null ? groupsWhereJoinCodeUsed.size() : "null");
        // what follows is somewhat expensive, but is fortunately going to be called quite rarely
        if (groupsWhereJoinCodeUsed != null && !groupsWhereJoinCodeUsed.isEmpty()) {
            logger.info("People joined groups today via a join code! Processing for {} groups", groupsWhereJoinCodeUsed.size());
            for (Group group : groupsWhereJoinCodeUsed) {
                List<String> joinedUserDescriptions;
                List<GroupLog> groupLogs = groupLogRepository.findByGroupAndGroupLogTypeAndCreatedDateTimeBetween(group,
                        GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE,
                        periodStart, periodEnd);

                Set<User> organizers = group.getMemberships().stream() // consider adding a getOrganizers method to group
                        .filter(m -> m.getRole().getName().equals(BaseRoles.ROLE_GROUP_ORGANIZER))
                        .map(Membership::getUser).collect(Collectors.toSet());

                if (groupLogs.size() < 4) { // create explicit list of phone numbers / display names to send to people
                    joinedUserDescriptions = new ArrayList<>();
                    for (GroupLog log : groupLogs)
                        joinedUserDescriptions.add(userRepository.findOne(log.getUser().getId()).nameToDisplay());
                } else {
                    joinedUserDescriptions = null;
                }

                for (User user : organizers) {
                    String message = messageAssemblingService.
                            createGroupJoinCodeUseMessage(user, group.getGroupName(), groupLogs.size(), joinedUserDescriptions);
                    logger.info("Will send {} this message: {}", user.nameToDisplay(), message);
                }
            }
        }
    }


    @Override
    @Transactional
    @Async
    public void asyncAddMemberships(String initiatorUid, String groupId, Set<MembershipInfo> membershipInfos,
                                    GroupJoinMethod joinMethod, String joinMethodDescriptor, boolean duringGroupCreation, boolean createWelcomeNotifications) {
        User initiator = userRepository.findOneByUid(initiatorUid);
        Group group = groupRepository.findOneByUid(groupId);
        LogsAndNotificationsBundle bundle = addMemberships(initiator, group, membershipInfos, joinMethod, joinMethodDescriptor,
                duringGroupCreation, createWelcomeNotifications);
        logsAndNotificationsBroker.storeBundle(bundle);
    }

    private LogsAndNotificationsBundle addMemberships(User initiator, Group group, Set<MembershipInfo> membershipInfos,
                                                      GroupJoinMethod joinMethod, String joinMethodDescriptor,
                                                      boolean duringGroupCreation, boolean createWelcomeNotifications) {
        // note: User objects should only ever store phone numbers in the msisdn format (i.e, with country code at front, no '+')

        Comparator<MembershipInfo> byPhoneNumber =
                (MembershipInfo m1, MembershipInfo m2) -> (m1.getPhoneNumberWithCCode().compareTo(m2.getPhoneNumberWithCCode()));

        Set<MembershipInfo> validNumberMembers = membershipInfos.stream()
                .filter(MembershipInfo::hasValidPhoneOrEmail)
                .collect(collectingAndThen(toCollection(() -> new TreeSet<>(byPhoneNumber)), HashSet::new));
        logger.debug("number of members: {}", validNumberMembers.size());

        Set<String> memberPhoneNumbers = validNumberMembers.stream()
                .map(MembershipInfo::getPhoneNumberWithCCode)
                .collect(Collectors.toSet());
        Set<String> emailAddresses = validNumberMembers.stream()
                .filter(MembershipInfo::hasValidEmail)
                .map(MembershipInfo::getMemberEmail).collect(Collectors.toSet());

        logger.debug("phoneNumbers returned: ...." + memberPhoneNumbers);

        Set<User> existingUsers = new HashSet<>(userRepository.findByPhoneNumberIn(memberPhoneNumbers));
        existingUsers.addAll(userRepository.findByEmailAddressIn(emailAddresses));

        Map<String, User> existingUserPhoneMap = existingUsers.stream()
                .filter(User::hasPhoneNumber)
                .collect(Collectors.toMap(User::getPhoneNumber, user -> user));
        Map<String, User> existingUserEmailMap = existingUsers.stream()
                .filter(User::hasEmailAddress)
                .collect(Collectors.toMap(User::getEmailAddress, user -> user));

        logger.info("Number of existing users ... {}", existingUsers.size() + existingUserEmailMap.size());

        Set<User> createdUsers = new HashSet<>();
        Set<Membership> memberships = new HashSet<>();

        Set<String> existingEmails = userRepository.fetchUsedEmailAddresses();
        Set<String> existingPhoneNumbers = userRepository.fetchUserPhoneNumbers();

        for (MembershipInfo membershipInfo : validNumberMembers) {
            // note: splitting this instead of getOrDefault, since that method calls default method even if it finds something, hence spurious user creation
            String msidn = membershipInfo.getPhoneNumberWithCCode();
            String emailAddress = membershipInfo.getMemberEmail();

            User user = existingUserPhoneMap.containsKey(msidn) ? existingUserPhoneMap.get(msidn) :
                    existingUserEmailMap.get(emailAddress);

            if (user == null) {
                logger.debug("Adding a new user, via group creation, with phone number ... " + msidn);
                user = new User(msidn, membershipInfo.getDisplayName(), emailAddress);
                createdUsers.add(user);
            } else if (!user.isHasInitiatedSession()) {
                if (!user.hasEmailAddress() && !StringUtils.isEmpty(emailAddress) && !existingEmails.contains(emailAddress)) {
                    user.setEmailAddress(emailAddress);
                }
                if (!user.hasPhoneNumber() && !StringUtils.isEmpty(msidn) && !existingPhoneNumbers.contains(msidn)) {
                    user.setPhoneNumber(msidn);
                }
            }

            String roleName = membershipInfo.getRoleName();

            if (roleName == null)
                roleName = BaseRoles.ROLE_ORDINARY_MEMBER;
            if (joinMethod == null)
                joinMethod = GroupJoinMethod.ADDED_BY_OTHER_MEMBER;

            Membership membership = group.addMember(user, roleName, joinMethod, joinMethodDescriptor);

            if (user.getProvince() == null && membershipInfo.getProvince() != null) {
                user.setProvince(membershipInfo.getProvince());
            }

            if (membership != null) {
                if (membershipInfo.getTopics() != null) {
                    membership.setTopics(new HashSet<>(membershipInfo.getTopics()));
                }

                if (membershipInfo.getAffiliations() != null) {
                    membership.addAffiliations(new HashSet<>(membershipInfo.getAffiliations()));
                }

                memberships.add(membership);
            }
        }

        logger.info("completed iteration, added {} users", validNumberMembers.size());
        // make sure the newly created users are stored
        userRepository.save(createdUsers);
        userRepository.flush();

        // adding action logs and event notifications ...
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (User createdUser : createdUsers) {
            bundle.addLog(new UserLog(createdUser.getUid(), UserLogType.CREATED_IN_DB, String.format("Created by being added to group with ID: %s", group.getUid()), UNKNOWN));
        }

        final GroupLogType logType = duringGroupCreation ? GroupLogType.GROUP_MEMBER_ADDED_AT_CREATION : GroupLogType.GROUP_MEMBER_ADDED;

        Set<String> addedUserUids = new HashSet<>();
        for (Membership membership : memberships) {
            User member = membership.getUser();
            GroupLog groupLog = new GroupLog(group, initiator, logType, member, null, null, null);
            bundle.addLog(groupLog);
            notifyNewMembersOfUpcomingMeetings(bundle, member, group, groupLog);
            addedUserUids.add(member.getUid());
        }

        logger.info("Done with member add subroutine, returning bundle, with {} UIDs", addedUserUids.size());

        if (createWelcomeNotifications)
            triggerWelcomeMessagesAfterCommit(initiator.getUid(), group.getUid(), addedUserUids);

        if (group.getParent() != null) {
            triggerAddMembersToParentGroup(initiator, group.getParent(), membershipInfos, joinMethod);
        }

        return bundle;
    }

    private void triggerAddMembersToParentGroup(User initiator, Group group, Set<MembershipInfo> membershipInfos,
                                                GroupJoinMethod joinMethod) {
        AfterTxCommitTask afterTxCommitTask = () -> {
            applicationContext.getBean(GroupBroker.class)
                    .asyncAddMemberships(initiator.getUid(), group.getUid(), membershipInfos, joinMethod, null, false, false);
        };
        applicationEventPublisher.publishEvent(afterTxCommitTask);
    }

    private void triggerWelcomeMessagesAfterCommit(String addingUserUid, String groupUid, Set<String> addedUserUids) {
        AfterTxCommitTask afterTxCommitTask = () -> {
            if (accountGroupBroker.isGroupOnAccount(groupUid)) { // maybe just use isPaidFor
                logger.info("Group is on account, generate some welcome notifications, for uids: {}", addedUserUids);
                accountGroupBroker.generateGroupWelcomeNotifications(addingUserUid, groupUid, addedUserUids);
            }
        };
        applicationEventPublisher.publishEvent(afterTxCommitTask);
    }

    // for each meeting that belongs to this group, or it belongs to one of parent groups and apply to subgroups,
    // we create event notification for new member, but in case when meeting belongs to parent group, then only if member
    // is not already contained in this ancestor group (otherwise, it already got the notification for such meetings)
    private void notifyNewMembersOfUpcomingMeetings(LogsAndNotificationsBundle bundle, User user, Group group, GroupLog groupLog) {
        @SuppressWarnings("unchecked")
        Set<Meeting> meetings = (Set) group.getUpcomingEventsIncludingParents(event -> event.getEventType().equals(EventType.MEETING));
        meetings.forEach(m -> {
            Group meetingGroup = m.getAncestorGroup();
            if (meetingGroup.equals(group) || !meetingGroup.hasMember(user)) {
                boolean appliesToMember = m.isAllGroupMembersAssigned() || m.getAssignedMembers().contains(user);
                if (appliesToMember) {
                    String message = messageAssemblingService.createEventInfoMessage(user, m);
                    bundle.addNotification(new EventInfoNotification(user, message, groupLog, m));
                }
            }
        });
    }


    private Set<ActionLog> removeMemberships(User initiator, Group group, Set<Membership> memberships) {
        Set<ActionLog> actionLogs = new HashSet<>();
        for (Membership membership : memberships) {
            group.removeMembership(membership);
            if (gcmRegistrationBroker != null && gcmRegistrationBroker.hasGcmKey(membership.getUser())) {
                try {
                    gcmRegistrationBroker.changeTopicSubscription(membership.getUser().getUid(), group.getUid(), false);
                } catch (IOException e) {
                    logger.error("Unable to unsubscribe member with uid={} from group topic ={}", membership.getUser(), group);
                }
            }
            actionLogs.add(new GroupLog(group, initiator, GroupLogType.GROUP_MEMBER_REMOVED,
                    membership.getUser(), null, null, null));
        }
        return actionLogs;
    }

    @Override
    @Transactional
    public void removeMembers(String userUid, String groupUid, Set<String> memberUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(memberUids);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);

        if (!memberUids.isEmpty()) {
            memberUids.remove(userUid); // to make sure user cannot remove themselves (unsubscribe, below, is for that)
        }

        logger.info("Removing members: group={}, memberUids={}, user={}", group, memberUids, user);

        Set<Membership> memberships = group.getMemberships().stream()
                .filter(membership -> memberUids.contains(membership.getUser().getUid()))
                .collect(Collectors.toSet());

        Set<ActionLog> actionLogs = removeMemberships(user, group, memberships);

        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    @Transactional
    public void unsubscribeMember(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        // since it's an unsubscribe, don't need to check permissions
        Group group = groupRepository.findOneByUid(groupUid);
        User user = userRepository.findOneByUid(userUid);

        Membership membership = group.getMembership(user);

        if (BaseRoles.ROLE_GROUP_ORGANIZER.equalsIgnoreCase(membership.getRole().getName())) {
            if (membershipRepository.count(MembershipSpecifications.groupOrganizers(group)) == 1) {
                throw new SoleOrganizerUnsubscribeException();
            }
        }

        Set<ActionLog> actionLogs = removeMemberships(user, group, Collections.singleton(membership));
        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    @Transactional
    public void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(memberUid);

        logger.info("changing member to this role: " + roleName);

        if (userUid.equals(memberUid))
            throw new IllegalArgumentException("A user cannot change ther own role: memberUid = " + memberUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!permissionBroker.isGroupPermissionAvailable(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS)) {
            permissionBroker.validateSystemRole(user, BaseRoles.ROLE_SYSTEM_ADMIN);
        }

        Membership membership = group.getMemberships().stream()
                .filter(membership1 -> membership1.getUser().getUid().equals(memberUid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no member under UID " + memberUid + " in group " + group));

        logger.info("Updating membership role: membership={}, roleName={}, user={}", membership, roleName, user);

        Set<ActionLog> actionLogs = changeMembersToRole(user, group, Collections.singleton(memberUid), group.getRole(roleName));
        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    @Transactional
    public void updateMembershipDetails(String userUid, String groupUid, String memberUid, String name, String phone, String email, Province province) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(memberUid);

        User member = userRepository.findOneByUid(memberUid);
        if (member.hasPassword() || member.isHasSetOwnName()) {
            throw new IllegalArgumentException("Error - member has already set their own details");
        }

        User changingUser = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        try {
            permissionBroker.validateGroupPermission(changingUser, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        } catch (AccessDeniedException e) {
            throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }

        List<String> detailsChanged = new ArrayList<>();
        if (province != null) {
            member.setProvince(province);
            detailsChanged.add("province: " + province);
        }

        if (!StringUtils.isEmpty(name)) {
            member.setDisplayName(name);
            detailsChanged.add("name: " + name);
        }

        if (!StringUtils.isEmpty(phone)) {
            member.setPhoneNumber(PhoneNumberUtil.convertPhoneNumber(phone));
            detailsChanged.add("phone: " + phone);
        }

        if (!StringUtils.isEmpty(email)) {
            member.setEmailAddress(email);
            detailsChanged.add("email: " + email);
        }

        if (!detailsChanged.isEmpty()) {
            UserLog userLog = new UserLog(member.getUid(), UserLogType.DETAILS_CHANGED_BY_GROUP,
                    changingUser.getUid() + " : " + String.join(", ", detailsChanged),
                    UserInterfaceType.UNKNOWN);
            logActionLogsAfterCommit(Collections.singleton(userLog));
        }

    }

    @Override
    @Transactional
    public void assignMembershipTopics(String userUid, String groupUid, String memberUid, Set<String> topics) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(memberUid);
        Objects.requireNonNull(topics);

        User assigningUser = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        User alteredUser = userRepository.findOneByUid(memberUid);
        Membership member = group.getMembership(alteredUser);

        if (!alteredUser.equals(assigningUser)) {
            try {
                permissionBroker.validateGroupPermission(assigningUser, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
            } catch (AccessDeniedException e) {
                throw new MemberLacksPermissionException(Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
            }
        }

        List<String> groupTopics = group.getTopics();
        if (topics.stream().anyMatch(s -> !groupTopics.contains(s))) {
            throw new GroupTopicMismatchException();
        }

        member.setTopics(topics);
    }

    @Override
    @Transactional
    public boolean setGroupPinnedForUser(String userUid, String groupUid, boolean pinned) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Membership membership = membershipRepository.findByGroupUidAndUserUid(groupUid, userUid);
        if (membership != null) {
            membership.setViewPriority(pinned ? GroupViewPriority.PINNED : GroupViewPriority.NORMAL);
            membershipRepository.save(membership);
            return true;
        }
        return false;
    }

    @Override
    @Transactional
    public boolean updateViewPriority(String userUid, String groupUid, GroupViewPriority priority) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Membership membership = membershipRepository.findByGroupUidAndUserUid(groupUid, userUid);
        if (membership != null) {
            membership.setViewPriority(priority);
            return true;
        }
        return false;
    }

    private Set<ActionLog> changeMembersToRole(User user, Group group, Set<String> memberUids, Role newRole) {
        return group.getMemberships().stream()
                .filter(m -> memberUids.contains(m.getUser().getUid()))
                .peek(m -> m.setRole(newRole))
                .map(m -> new GroupLog(group, user, GroupLogType.GROUP_MEMBER_ROLE_CHANGED, m.getUser(), null, null, newRole.getName()))
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional
    public void updateMembers(String userUid, String groupUid, Set<MembershipInfo> modifiedMembers, boolean checkForDeletion) {

        // note: a simpler way to do this might be to in effect replace the members, but then will create issues with logging
        // also, we want to check permissions separately, hence ...
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Set<Membership> memberships = group.getMemberships();

        Set<String> modifiedPhoneNumbers = modifiedMembers.stream().map(MembershipInfo::getPhoneNumberWithCCode).collect(Collectors.toSet());
        Set<Membership> membershipsToRemove = memberships.stream()
                .filter(membership -> !modifiedPhoneNumbers.contains(membership.getUser().getPhoneNumber()))
                .collect(Collectors.toSet());

        Set<MembershipInfo> membersToAdd = new HashSet<>();
        Set<MembershipInfo> currentMembershipInfos = MembershipInfo.createFromMembers(memberships);

        for (MembershipInfo m : modifiedMembers) {
            if (currentMembershipInfos.contains(m)) {
                // todo: this seems incredibly inefficient, figure out how to do without all these entity loads
                User savedUser = userRepository.findByPhoneNumberAndPhoneNumberNotNull(m.getPhoneNumber());
                Membership savedMembership = group.getMembership(savedUser);
                if (!savedMembership.getRole().getName().equals(m.getRoleName())) {
                    updateMembershipRole(userUid, groupUid, savedUser.getUid(), m.getRoleName());
                }
            } else {
                // note: could also just do this with a removeAll, but since we're running the contain check, may as well
                membersToAdd.add(m);
            }
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if (checkForDeletion && !membershipsToRemove.isEmpty()) {
            // note: only call if non-empty to avoid throwing no permission error if user hasn't removed anyone
            Set<ActionLog> removeMembershipsLogs = removeMemberships(user, group, membershipsToRemove);
            bundle.addLogs(removeMembershipsLogs);
        }

        if (!membersToAdd.isEmpty() && checkGroupSizeLimit(group, membersToAdd.size())) {
            // note: as above, only call if non-empty so permission check only happens
            // and since by definition this is only ever called by group organizer, method is added by other
            LogsAndNotificationsBundle addMembershipsBundle = addMemberships(user, group, membersToAdd,
                    GroupJoinMethod.ADDED_BY_OTHER_MEMBER, user.getName(), false, true);
            bundle.addBundle(addMembershipsBundle);
        }

        logsAndNotificationsBroker.asyncStoreBundle(bundle);
    }

    @Override
    @Transactional
    public Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                       boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(firstGroupUid);
        Objects.requireNonNull(secondGroupUid);

        User user = userRepository.findOneByUid(userUid);

        Group firstGroup = groupRepository.findOneByUid(firstGroupUid);
        Group secondGroup = groupRepository.findOneByUid(secondGroupUid);

        if (!checkGroupSizeLimit(firstGroup, secondGroup.getMemberships().size())) {
            throw new GroupSizeLimitExceededException();
        }

        logger.info("Merging groups: firstGroup={}, secondGroup={}, user={}, leaveActive={}, orderSpecified={}, createNew={}, newGroupName={}",
                firstGroup, secondGroup, user, leaveActive, orderSpecified, createNew, newGroupName);

        Group groupInto, groupFrom;
        if (orderSpecified || firstGroup.getMemberships().size() > secondGroup.getMemberships().size()) {
            groupInto = firstGroup;
            groupFrom = secondGroup;
        } else {
            groupInto = secondGroup;
            groupFrom = firstGroup;
        }

        permissionBroker.validateGroupPermission(user, groupInto, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        if (!leaveActive) {
            permissionBroker.validateGroupPermission(user, groupFrom, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        }

        Group resultGroup;

        if (createNew) {
            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupInto.getMemberships());
            membershipInfos.addAll(MembershipInfo.createFromMembers(groupFrom.getMemberships()));
            resultGroup = create(user.getUid(), newGroupName, null, membershipInfos, GroupPermissionTemplate.DEFAULT_GROUP, null, null, false, false);
            if (!leaveActive) {
                deactivate(user.getUid(), groupInto.getUid(), false);
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }
        } else {

            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupFrom.getMemberships());
            LogsAndNotificationsBundle bundle = addMemberships(user, groupInto, membershipInfos,
                    GroupJoinMethod.COPIED_INTO_GROUP, groupFrom.getName(), false, true);
            resultGroup = groupInto;
            if (!leaveActive) {
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }

            logsAndNotificationsBroker.asyncStoreBundle(bundle);
        }

        logger.info("Group from active status is now : {}", groupFrom.isActive());
        return resultGroup;
    }

    @Override
    @Transactional
    public void updateGroupPermissions(String userUid, String groupUid, Map<String, Set<Permission>> newPermissions) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(newPermissions);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE);

        // note: the UI also needs to enforce protection of organizer permissions, but since this is delicate and
        // potentially irreversible, also enforcing it here

        for (Role role : group.getGroupRoles()) {
            Objects.requireNonNull(newPermissions.get(role.getName()));
            Set<Permission> adjustedRolePermissions = newPermissions.get(role.getName());
            if (role.getName().equals(BaseRoles.ROLE_GROUP_ORGANIZER)) {
                adjustedRolePermissions.addAll(permissionBroker.getProtectedOrganizerPermissions());
            }
            role.setPermissions(adjustedRolePermissions);
        }

        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user,
                GroupLogType.PERMISSIONS_CHANGED, "Changed permissions assigned to group roles")));

    }

    @Override
    @Transactional
    public void updateGroupPermissionsForRole(String userUid, String groupUid, String roleName, Set<Permission> permissionsToAdd, Set<Permission> permissionsToRemove) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(permissionsToAdd);
        Objects.requireNonNull(permissionsToRemove);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_CHANGE_PERMISSION_TEMPLATE);

        Role roleToUpdate = group.getRole(roleName);
        Set<Permission> updatedPermissions = new HashSet<>(roleToUpdate.getPermissions());
        updatedPermissions.removeAll(permissionsToRemove);
        updatedPermissions.addAll(permissionsToAdd);
	    if (roleName.equals(BaseRoles.ROLE_GROUP_ORGANIZER)) {
		    updatedPermissions.addAll(permissionBroker.getProtectedOrganizerPermissions());
	    }

        roleToUpdate.setPermissions(updatedPermissions);

        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user,
                GroupLogType.PERMISSIONS_CHANGED, "Changed permissions assigned to " + roleName)));

    }

    @Override
    @Transactional
    public void updateMemberAlias(String userUid, String groupUid, String alias) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Membership membership = group.getMembership(user);
        membership.setAlias(alias);

        logActionLogsAfterCommit(Collections.singleton(
                new GroupLog(group, user, GroupLogType.CHANGED_ALIAS, user, null, null, alias)
        ));
    }

    @Override
    @Transactional
    public void combinedEdits(String userUid, String groupUid, String groupName, String description, boolean resetToDefaultImage, GroupDefaultImage defaultImage,
                              boolean discoverable, boolean toCloseJoinCode, Set<String> membersToRemove, Set<String> organizersToAdd, int reminderMinutes) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        Set<ActionLog> groupLogs = new HashSet<>();

        if (!StringUtils.isEmpty(groupName) && !group.getName().equals(groupName.trim())) {
            group.setGroupName(groupName);
            groupLogs.add(new GroupLog(group, user, GroupLogType.GROUP_RENAMED, groupName));
        }

        if (!StringUtils.isEmpty(description) && !group.getDescription().equals(description.trim())) {
            group.setDescription(description);
            groupLogs.add(new GroupLog(group, user, GroupLogType.GROUP_DESCRIPTION_CHANGED, description));
        }

        if (resetToDefaultImage && defaultImage != null) {
            group.setDefaultImage(defaultImage);
            group.setImage(null);
            group.setImageUrl(null);
            groupLogs.add(new GroupLog(group, user, GroupLogType.GROUP_AVATAR_REMOVED, defaultImage.toString()));
        }

        if (group.isDiscoverable() != discoverable) {
            group.setDiscoverable(discoverable);
            groupLogs.add(new GroupLog(group, user, GroupLogType.DISCOVERABLE_CHANGED, "set to " + discoverable));
        }

        if (toCloseJoinCode) {
            group.setGroupTokenCode(null);
            group.setTokenExpiryDateTime(Instant.now());
            groupLogs.add(new GroupLog(group, user, GroupLogType.TOKEN_CHANGED, "token closed"));
        }

        if (membersToRemove != null && !membersToRemove.isEmpty()) {
            membersToRemove.remove(userUid); // in case (will fail silently if user not part of set)
            Set<Membership> memberships = group.getMemberships().stream()
                    .filter(membership -> membersToRemove.contains(membership.getUser().getUid()))
                    .collect(Collectors.toSet());
            groupLogs.addAll(removeMemberships(user, group, memberships));
        }

        if (organizersToAdd != null && !organizersToAdd.isEmpty()) {
            groupLogs.addAll(changeMembersToRole(user, group, organizersToAdd, group.getRole(BaseRoles.ROLE_GROUP_ORGANIZER)));
        }

        if(group.getReminderMinutes() != reminderMinutes){
            group.setReminderMinutes(reminderMinutes);
            groupLogs.add(new GroupLog(group, user, GroupLogType.REMINDER_DEFAULT_CHANGED, "group reminder changed to " + reminderMinutes));
        }

        if (!groupLogs.isEmpty()) {
            logger.info("Combination of edits done! There are {}, and they are {}", groupLogs.size(), groupLogs);
            logActionLogsAfterCommit(groupLogs);
        }
    }

    @Override
    @Transactional
    public Group loadAndRecordUse(String groupUid, String code) {
        Group group = load(groupUid);
        validateJoinCode(group, code);
        recordJoinCodeInbound(group, code);
        // in fugure may add some sophistication here, e.g., deciding what to send back, etc
        return group;
    }


    @Override
    @Transactional
    public void updateGroupDefaultReminderSetting(String userUid, String groupUid, int reminderMinutes) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(reminderMinutes);

        Group group = load(groupUid);
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // todo: think about whether to change any events currently pending & set to group default
        group.setReminderMinutes(reminderMinutes);
        String logMessage = String.format("Changed reminder default to %d minutes", reminderMinutes);
        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user, GroupLogType.REMINDER_DEFAULT_CHANGED, logMessage)));
    }

    @Override
    @Transactional
    public void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups) {
        logger.info("Inside the group language setting function ...");
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        Group group = load(groupUid);;
        User user = userRepository.findOneByUid(userUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        Set<User> groupMembers = group.getMembers();

        for (User member : groupMembers) {
            if (!member.isHasInitiatedSession()) {
                logger.info("User hasn't set their own language, so adjusting it to: " + newLocale + " for this user: " + member.nameToDisplay());
                member.setLanguageCode(newLocale);
            }
        }
        group.setDefaultLanguage(newLocale);

        if (includeSubGroups) {
            groupRepository.findAll(Specifications.where(
                    GroupSpecifications.hasParent(group)).and(GroupSpecifications.isActive()))
            .forEach(g -> {
                try {
                    updateGroupDefaultLanguage(userUid, g.getUid(), newLocale, true);
                } catch (AccessDeniedException e) {
                    logger.info("Skipping subgroup as permissions don't apply");
                }
            });
        }

        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user, GroupLogType.LANGUAGE_CHANGED,
                                                                     String.format("Set default language to %s", newLocale))));

    }

    @Override
    @Transactional
    public void updateTopics(String userUid, String groupUid, Set<String> topics) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(topics);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setTopics(topics);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean canAddMember(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return checkGroupSizeLimit(group, 1);
    }

    @Override
    @Transactional(readOnly = true)
    public int numberMembersBeforeLimit(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return membersLeftForGroup(group);
    }

    @Override
    @Transactional
    public String openJoinToken(String userUid, String groupUid, LocalDateTime expiryDateTime) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        // cases where a user will have update details but not add member will be few, but given sensitivity here, double checking
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);

        JoinTokenOpeningResult result = openJoinTokenInternal(user, group, expiryDateTime);

        logActionLogsAfterCommit(Collections.singleton(result.getGroupLog()));

        return result.getToken();
    }

    private JoinTokenOpeningResult openJoinTokenInternal(User user, Group group, LocalDateTime expiryDateTime) {

        final Instant currentExpiry = (group.getTokenExpiryDateTime() != null) ? group.getTokenExpiryDateTime() : null;
        final Instant expirySystemTime = expiryDateTime == null ? getVeryLongAwayInstant() : convertToSystemTime(expiryDateTime, getSAST());

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        logger.info("Opening join token: user={}, group={}, expiryDateTime={}", user, group, expiryDateTime);

        boolean temporary = expiryDateTime != null;

        // if there is already a valid token we are just changing expiry and returning it
        String token, logMessage;
        if (currentExpiry != null && currentExpiry.isAfter(Instant.now())) {
            if (!temporary) {
                group.setTokenExpiryDateTime(getVeryLongAwayInstant());
            } else if (expiryDateTime != null) {
                group.setTokenExpiryDateTime(expirySystemTime);
            }
            token = group.getGroupTokenCode();
            logMessage = temporary ? String.format("Changed group joining code closing time to %s", expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                    : "Extended group joining code to permanent";
        } else {
            logger.info("Generating a group join code that is open ...");
            token = tokenGeneratorService.getNextToken();
            group.setTokenExpiryDateTime((temporary) ? expirySystemTime : getVeryLongAwayInstant());
            group.setGroupTokenCode(token);
            logMessage = temporary ?
                    String.format("Created join code, %s, with closing time %s", token, expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME)) :
                    String.format("Created join code %s, to remain open until closed by group", token);
        }

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.TOKEN_CHANGED, logMessage);

        return new JoinTokenOpeningResult(token, groupLog);
    }

    private static class JoinTokenOpeningResult {
        private final String token;
        private final GroupLog groupLog;

        public JoinTokenOpeningResult(String token, GroupLog groupLog) {
            this.token = Objects.requireNonNull(token);
            this.groupLog = Objects.requireNonNull(groupLog);
        }

        public String getToken() {
            return token;
        }

        public GroupLog getGroupLog() {
            return groupLog;
        }

        @Override
        public String toString() {
            final StringBuilder sb = new StringBuilder("JoinTokenOpeningResult{");
            sb.append("token='").append(token).append('\'');
            sb.append(", groupLog=").append(groupLog);
            sb.append('}');
            return sb.toString();
        }
    }

    @Override
    @Transactional
    public void closeJoinToken(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setGroupTokenCode(null);
        group.setTokenExpiryDateTime(Instant.now());

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.TOKEN_CHANGED, "Group join code closed");
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional
    public GroupJoinCode addJoinTag(String userUid, String groupUid, String code, String urlToShorten) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        if (groupJoinCodeRepository.countByGroupUidAndActiveTrue(groupUid) > maxJoinWords) {
            throw new JoinWordsExceededException();
        }

        Set<String> currentTags = groupJoinCodeRepository.selectActiveJoinWords();
        if (currentTags.contains(code.toLowerCase())) {
            throw new IllegalArgumentException("Error! Passed an already taken join code");
        }

        GroupJoinCode gjc  = new GroupJoinCode(user, group, code, JoinCodeType.JOIN_WORD);
        if (urlToShorten != null) {
            gjc.setShortUrl(urlShortener.shortenGroupJoinUrls(urlToShorten));
        }

        gjc = groupJoinCodeRepository.saveAndFlush(gjc);

        // note: shouldn't need to add to group, Hibernate should wire up, but keep an eye out

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.JOIN_CODE_ADDED, code);
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle(Collections.singleton(groupLog), Collections.EMPTY_SET);
        logsAndNotificationsBroker.storeBundle(bundle);

        return gjc;
    }

    @Override
    @Transactional
    public void removeJoinTag(String userUid, String groupUid, String code) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        logger.info("looking for code: {}", code);
        GroupJoinCode gjc = groupJoinCodeRepository.findByGroupUidAndCodeAndActiveTrue(group.getUid(), code);

        if (gjc != null) {
            gjc.setActive(false);
            gjc.setClosedTime(Instant.now());
            gjc.setClosingUser(user);
        } else {
            logger.error("Asked to close an already closed or non-existing join code, {}, for group {}", code, group);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public Set<String> getUsedJoinWords() {
        return groupJoinCodeRepository.selectActiveJoinWords();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, String> getJoinWordsWithGroupIds() {
        return groupJoinCodeRepository.findByActiveTrue()
                .stream().collect(Collectors.toMap(GroupJoinCode::getCode, gjc -> gjc.getGroup().getUid()));
    }

    @Override
    @Transactional
    public void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        DebugUtil.transactionRequired("");

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        String logEntry;
        if (discoverable) {
            User authorizer = (authUserPhoneNumber == null) ? user : userRepository.findByPhoneNumberAndPhoneNumberNotNull(authUserPhoneNumber);
            group.setDiscoverable(true);
            group.setJoinApprover(authorizer);
            logEntry = "Set group publicly discoverable, with join approver " + authorizer.nameToDisplay();
            logger.info(logEntry);
        } else {
            group.setJoinApprover(null);
            group.setDiscoverable(false);
            logEntry = "Set group hidden from public";
        }

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.DISCOVERABLE_CHANGED, logEntry);
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional
    public void link(String userUid, String childGroupUid, String parentGroupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(childGroupUid);
        Objects.requireNonNull(parentGroupUid);

        User user = userRepository.findOneByUid(userUid);
        Group child = groupRepository.findOneByUid(childGroupUid);
        Group parent = groupRepository.findOneByUid(parentGroupUid);

        permissionBroker.validateGroupPermission(user, parent, Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
        permissionBroker.validateGroupPermission(user, child, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        child.setParent(parent);

        Set<ActionLog> actionLogs = new HashSet<>();
        actionLogs.add(new GroupLog(parent, user, GroupLogType.SUBGROUP_ADDED, null, child, null, "Subgroup added"));
        actionLogs.add(new GroupLog(child, user, GroupLogType.PARENT_CHANGED, null, parent, null, "Parent group added or changed"));
        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    @Transactional
    public void addMemberViaCampaign(String userUidToAdd, String groupUid,String campaignCode) {
        User user = userRepository.findOneByUid(userUidToAdd);
        Group group = groupRepository.findOneByUid(groupUid);
        logger.info("Adding a member via campaign add request: group={}, user={}, code={}", group, user, campaignCode);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.SELF_JOINED, null);
        Group parentGroup = group.getParent();
        while (parentGroup != null) {
            parentGroup.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER, GroupJoinMethod.ADDED_BY_OTHER_MEMBER, null);
            parentGroup = parentGroup.getParent();
        }
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_MEMBER_ADDED_VIA_CAMPAIGN,
                user, null, null, "Member joined via campaign code: " + campaignCode);
        bundle.addLog(groupLog);
        bundle.addLog(new UserLog(userUidToAdd, UserLogType.USED_A_CAMPAIGN, groupUid, UNKNOWN));
        notifyNewMembersOfUpcomingMeetings(bundle, user, group, groupLog);
        storeBundleAfterCommit(bundle);
    }


    @Override
    @Transactional
    public void sendGroupJoinCodeNotification(String userUid, String groupUid) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        String message = messageAssemblingService.createGroupJoinCodeMessage(group);

        UserLog userLog = new UserLog(user.getUid(), UserLogType.SENT_GROUP_JOIN_CODE,
                "Group join code sent", UserInterfaceType.UNKNOWN);

        Notification notification = new JoinCodeNotification(user,message,userLog);

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
        bundle.addLog(userLog);
        bundle.addNotification(notification);
        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional
    public void sendAllGroupJoinCodesNotification(String userUid) {
        User user = userRepository.findOneByUid(userUid);

        //List<Group> groups = groupRepository.findByCreatedByUserAndActiveTrueOrderByCreatedDateTimeDesc(user);
        List<Group> groups = permissionBroker.getActiveGroupsSorted(user,null);

        UserLog userLog = new UserLog(user.getUid(), UserLogType.SENT_GROUP_JOIN_CODE,
                "All groups join codes sent", UserInterfaceType.UNKNOWN);

        List<String> strings = messageAssemblingService.getMessagesForGroups(groups);
        logger.info("List Size....={}",strings.size());
        for (String s:strings){
            Notification notification = new JoinCodeNotification(user,s,userLog);
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addLog(userLog);
            bundle.addNotification(notification);
            logsAndNotificationsBroker.storeBundle(bundle);
            logger.info("MSG....={}",s);
            logger.info("Length....={}",s.length());
        }
    }

    private boolean checkGroupSizeLimit(Group group, int numberOfMembersAdding) {
        return membersLeftForGroup(group) > numberOfMembersAdding;
    }

    private int membersLeftForGroup(Group group) {
        Account account = accountGroupBroker.findAccountForGroup(group.getUid());
        int currentMembers = group.getMemberships().size();
        return !limitGroupSize ? 99999 :
                account == null ? Math.max(0, freeGroupSizeLimit - currentMembers) :
                        Math.max(0, account.getMaxSizePerGroup() - currentMembers);
    }

}
