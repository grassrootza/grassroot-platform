package za.org.grassroot.services;

import com.google.common.collect.Sets;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.async.AsyncGroupEventLogger;
import za.org.grassroot.services.async.AsyncUserLogger;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;
import za.org.grassroot.services.util.TokenGeneratorService;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

import static za.org.grassroot.core.util.DateTimeUtil.*;

@Service
public class GroupBrokerImpl implements GroupBroker {

    private final Logger logger = LoggerFactory.getLogger(GroupBrokerImpl.class);

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private EventRepository eventRepository;
    @Autowired
    private MeetingRepository meetingRepository;
    @Autowired
    private VoteRepository voteRepository;
    @Autowired
    private GroupLogRepository groupLogRepository;

    @Autowired
    private PermissionBroker permissionBroker;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private AsyncGroupEventLogger asyncGroupEventLogger;
    @Autowired
    private AsyncUserLogger asyncUserLogger;
    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Override
    @Transactional(readOnly = true)
    public Group load(String groupUid) {
        return groupRepository.findOneByUid(groupUid);
    }

    @Override
    @Transactional
    public Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos, GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(membershipInfos);
        Objects.requireNonNull(groupPermissionTemplate);

        User user = userRepository.findOneByUid(userUid);

        Group parent = null;
        if (parentGroupUid != null) {
            parent = groupRepository.findOneByUid(parentGroupUid);
        }

        logger.info("Creating new group: name={}, description={}, membershipInfos={}, groupPermissionTemplate={},  parent={}, user={}",
                name, description, membershipInfos, groupPermissionTemplate, parent, user);
        //todo: refactor and include description
        Group group = new Group(name, user);
        GroupLog groupAddedEventLog;
        if (parent == null) {
            groupAddedEventLog = new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_ADDED, null);
        } else {
            group.setParent(parent);
            groupAddedEventLog = new GroupLog(parent.getId(), user.getId(), GroupLogType.GROUP_ADDED, group.getId());
        }

        Set<Membership> memberships = addMembers(user, group, membershipInfos);
        permissionBroker.setRolePermissionsFromTemplate(group, groupPermissionTemplate);

        // last: set some advanced features, with defaults in case null passed
        group.setDescription((description == null) ? "" : description);
        group.setReminderMinutes((reminderMinutes == null) ? (24 * 60) : reminderMinutes);

        group = groupRepository.save(group);

        logger.info("Group created under UID {}", group.getUid());

        // we record event in async manner, after this TX has committed
        Set<GroupLog> groupLogs = new HashSet<>();
        groupLogs.add(groupAddedEventLog);
        for (Membership membership : memberships) {
            groupLogs.add(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_ADDED, membership.getUser().getId()));
        }

        logGroupEventsAfterCommit(groupLogs);

        return group;
    }

    private void logGroupEventsAfterCommit(Set<GroupLog> groupLogs) {
        if (!groupLogs.isEmpty()) {
            // we want to log group events after transaction has committed
            AfterTxCommitTask afterTxCommitTask = () -> asyncGroupEventLogger.logGroupEvents(groupLogs);
            applicationEventPublisher.publishEvent(afterTxCommitTask);
        }
    }

    private void logUserCreationAfterCommit(Set<String> newUserUids, String description) {
        if (!newUserUids.isEmpty()) {
            AfterTxCommitTask afterTxCommitTask = () -> asyncUserLogger.logUserCreation(newUserUids, description);
            applicationEventPublisher.publishEvent(afterTxCommitTask);
        }
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

        GroupLog groupAddedEventLog;
        if (group.getParent() == null) {
            groupAddedEventLog = new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_REMOVED, null);
        } else {
            groupAddedEventLog = new GroupLog(group.getParent().getId(), user.getId(), GroupLogType.GROUP_REMOVED, group.getId());
        }

        Set<GroupLog> groupLogs = Collections.singleton(groupAddedEventLog);
        logGroupEventsAfterCommit(groupLogs);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isDeactivationAvailable(User user, Group group, boolean checkIfWithinTimeWindow) {
        // todo: Integrate with permission checking -- for now, just checking if group created by user in last 48 hours
        boolean isUserGroupCreator = group.getCreatedByUser().equals(user);
        if (!checkIfWithinTimeWindow) {
            return isUserGroupCreator;
        } else {
            Instant deactivationTimeThreshold = group.getCreatedDateTime().toInstant().plus(Duration.ofHours(48));
            return isUserGroupCreator && Instant.now().isBefore(deactivationTimeThreshold);
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

        Set<GroupLog> groupLogs = Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_RENAMED, group.getId(), "Group renamed to " + group.getGroupName()));
        logGroupEventsAfterCommit(groupLogs);
    }

    @Override
    public void updateDescription(String userUid, String groupUid, String description) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(description);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setDescription(description);
        Set<GroupLog> groupLogs = Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.DESCRIPTION_CHANGED,
                                                                     group.getId(), "Group description changed to " + group.getDescription()));
        logGroupEventsAfterCommit(groupLogs);
    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);

        logger.info("Adding members: group={}, memberships={}, user={}", group, membershipInfos, user);
        Set<Membership> memberships = addMembers(user, group, membershipInfos);

        Set<GroupLog> groupLogs = new HashSet<>();
        for (Membership membership : memberships) {
            groupLogs.add(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_ADDED, membership.getUser().getId()));
        }
        logGroupEventsAfterCommit(groupLogs);
    }

    @Override
    @Transactional
    public void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed) {
        User user = userRepository.findOneByUid(userUidToAdd);
        Group group = groupRepository.findOneByUid(groupUid);
        if (!tokenPassed.equals(group.getGroupTokenCode()) || Instant.now().isAfter(group.getTokenExpiryDateTime().toInstant()))
            throw new RuntimeException(""); // todo: create a custom version

        logger.info("Adding a member via token code: group={}, user={}, code={}", group, user, tokenPassed);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER);
        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_ADDED,
                                               user.getId(), "Member joined via join code: " + tokenPassed)));
    }

    private Set<Membership> addMembers(User initiator, Group group, Set<MembershipInfo> membershipInfos) {
        // note: User objects should only ever store phone numbers in the msisdn format (i.e, with country code at front, no '+')
        Set<String> memberPhoneNumbers = membershipInfos.stream().map(MembershipInfo::getPhoneNumberWithCCode).collect(Collectors.toSet());
        logger.info("phoneNumbers returned: ...." + memberPhoneNumbers);
        Set<User> existingUsers = new HashSet<>(userRepository.findByPhoneNumberIn(memberPhoneNumbers));
        Map<String, User> existingUserMap = existingUsers.stream().collect(Collectors.toMap(User::getPhoneNumber, user -> user));
        logger.info("Number of existing users ... " + existingUsers.size());

        Set<String> newlyCreatedUsers = new HashSet<>();
        Set<Membership> memberships = new HashSet<>();
        for (MembershipInfo membershipInfo : membershipInfos) {
            // note: splitting this instead of getOrDefault, since that method calls default method even if it finds something, hence spurious user creation
            User user = existingUserMap.get(membershipInfo.getPhoneNumberWithCCode());
            if (user == null) user = createNewUserAndAddToSet(membershipInfo.getPhoneNumberWithCCode(), membershipInfo.getDisplayName(), newlyCreatedUsers);
            String roleName = membershipInfo.getRoleName();
            Membership membership = roleName == null ? group.addMember(user) : group.addMember(user, roleName);
            if (membership != null) {
                memberships.add(membership);
            }
        }
        logUserCreationAfterCommit(newlyCreatedUsers, String.format("Created by being added to group with ID: %s", group.getUid()));
        return memberships;
    }

    private User createNewUserAndAddToSet(String phoneNumber, String displayName, Set<String> uidCollector) {
        logger.info("Adding a new user, via group creation, with phone number ... " + phoneNumber);
        User user = new User(phoneNumber, displayName);
        uidCollector.add(user.getUid());
        return user;
    }

    @Override
    @Transactional
    public void removeMembers(String userUid, String groupUid, Set<String> memberUids) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_DELETE_GROUP_MEMBER);

        logger.info("Removing members: group={}, memberUids={}, user={}", group, memberUids, user);

        Set<Membership> memberships = group.getMemberships().stream()
                .filter(membership -> memberUids.contains(membership.getUser().getUid()))
                .collect(Collectors.toSet());

        Set<GroupLog> groupLogs = new HashSet<>();
        for (Membership membership : memberships) {
            group.removeMembership(membership);
            groupLogs.add(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_REMOVED, membership.getUser().getId()));
        }

        logGroupEventsAfterCommit(groupLogs);
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
        group.removeMembership(membership);

        logGroupEventsAfterCommit(Sets.newHashSet(new GroupLog(group.getId(), user.getId(),
                                                               GroupLogType.GROUP_MEMBER_REMOVED, user.getId())));
    }

    @Override
    @Transactional
    public void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(memberUid);

        if (userUid.equals(memberUid))
            throw new IllegalArgumentException("A user cannot change ther own role: memberUid = " + memberUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        Membership membership = group.getMemberships().stream()
                .filter(membership1 -> membership1.getUser().getUid().equals(memberUid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no member under UID " + memberUid + " in group " + group));

        logger.info("Updating membership role: membership={}, roleName={}, user={}", membership, roleName, user);

        Role role = group.getRole(roleName);
        membership.setRole(role);
    }

    @Override
    @Transactional
    public void updateMembers(String userUid, String groupUid, Set<MembershipInfo> modifiedMembers) {

        // note: a simpler way to do this might be to in effect replace the members, but then will create issues with logging
        // also, we want to check permissions separately, hence ...

        Group savedGroup = groupRepository.findOneByUid(groupUid);

        Set<MembershipInfo> savedMembers = MembershipInfo.createFromMembers(savedGroup.getMemberships());

        Set<String> membersToRemove = new HashSet<>();
        Set<MembershipInfo> membersToAdd = new HashSet<>();

        // todo: replace this with a cleaner & faster (fewer query) removeAll followed by Map
        for (MembershipInfo memberInfo : savedMembers) {
            if (!modifiedMembers.contains(memberInfo)) {
                String uid = userRepository.findByPhoneNumber(memberInfo.getPhoneNumber()).getUid();
                membersToRemove.add(uid);
            }
        }

        for (MembershipInfo m : modifiedMembers) {
            if (savedMembers.contains(m)) {
                // todo: this seems incredibly inefficient, figure out how to do without all these entity loads
                User savedUser = userRepository.findByPhoneNumber(m.getPhoneNumber());
                Membership savedMembership = savedGroup.getMembership(savedUser);
                if (savedMembership.getRole().getName() != m.getRoleName())
                    updateMembershipRole(userUid, groupUid, savedUser.getUid(), m.getRoleName());
            } else {
                // note: could also just do this with a removeAll, but since we're running the contain check, may as well
                membersToAdd.add(m);
            }
        }

        if (!membersToRemove.isEmpty()) {
            // note: only call if non-empty to avoid throwing no permission error if user hasn't removed anyone
            removeMembers(userUid, groupUid, membersToRemove);
        }

        if (!membersToAdd.isEmpty()) {
            // note: as above, only call if non-empty so permission check only happens
            addMembers(userUid, groupUid, membersToAdd);
        }

        // might not be necessary, but adding just in case, given extent of changes ...
        // groupRepository.save(savedGroup);

    }

    public Group merge(String userUid, String firstGroupUid, String secondGroupUid,
                       boolean leaveActive, boolean orderSpecified, boolean createNew, String newGroupName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(firstGroupUid);
        Objects.requireNonNull(secondGroupUid);

        User user = userRepository.findOneByUid(userUid);
        Group firstGroup = groupRepository.findOneByUid(firstGroupUid);
        Group secondGroup = groupRepository.findOneByUid(secondGroupUid);

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

        Group resultGroup;

        if (createNew) {
            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupInto.getMemberships());
            membershipInfos.addAll(MembershipInfo.createFromMembers(groupFrom.getMemberships()));
            // todo: work out what to do about templates ... probably a UX issue more than solving here
            resultGroup = create(user.getUid(), newGroupName, null, membershipInfos, GroupPermissionTemplate.DEFAULT_GROUP, null, null);
            if (!leaveActive) {
                deactivate(user.getUid(), groupInto.getUid(), false);
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }
        } else {
            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupFrom.getMemberships());
            Set<Membership> memberships = addMembers(user, groupInto, membershipInfos);
            resultGroup = groupInto;
            if (!leaveActive) {
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }

            // logging group events about new members added
            Set<GroupLog> groupLogs = new HashSet<>();
            for (Membership membership : memberships) {
                groupLogs.add(new GroupLog(groupInto.getId(), user.getId(), GroupLogType.GROUP_MEMBER_ADDED, membership.getUser().getId()));
            }
            logGroupEventsAfterCommit(groupLogs);
        }

        logger.info("Group from active status is now : {}", groupFrom.isActive());
        groupRepository.saveAndFlush(groupFrom);
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

        // todo: consider more fine grained logging (which permission changed)
        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.PERMISSIONS_CHANGED, 0L,
                                               "Changed permissions assigned to group roles")));

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
        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(),
                                                                     GroupLogType.REMINDER_DEFAULT_CHANGED, 0L, logMessage)));
    }

    @Override
    @Transactional
    public void updateGroupDefaultLanguage(String userUid, String groupUid, String newLocale, boolean includeSubGroups) {
        logger.info("Inside the group language setting function ...");
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        Group group = load(groupUid);;
        User user = userRepository.findOneByUid(userUid);

        // since this might be called from a parent, via recursion, on which this will throw an error, rather catch & throw
        try {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        } catch (AccessDeniedException e) {
            return;
        }

        Set<User> groupMembers = group.getMembers();

        for (User member : groupMembers) {
            if (!member.isHasInitiatedSession()) {
                logger.info("User hasn't set their own language, so adjusting it to: " + newLocale + " for this user: " + member.nameToDisplay());
                member.setLanguageCode(newLocale);
            }
        }
        group.setDefaultLanguage(newLocale);

        if (includeSubGroups) {
            List<Group> subGroups = new ArrayList<>(groupRepository.findByParentAndActiveTrue(group));
            if (subGroups != null && !subGroups.isEmpty()) {
                for (Group subGroup : subGroups)
                    updateGroupDefaultLanguage(userUid, subGroup.getUid(), newLocale, true);
            }
        }

        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.LANGUAGE_CHANGED,
                                                                     0L, String.format("Set default language to %s", newLocale))));

    }

    @Override
    @Transactional
    public String openJoinToken(String userUid, String groupUid, boolean temporary, LocalDateTime expiryDateTime) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        String tokenToReturn, logMessage;
        LocalDateTime currentExpiry = (group.getTokenExpiryDateTime() != null) ? group.getTokenExpiryDateTime().toLocalDateTime() : null;
        final LocalDateTime endOfCentury = getVeryLongTimeAway();

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // if there is already a valid token we are just changing expiry and returning it
        if (currentExpiry != null && currentExpiry.isAfter(LocalDateTime.now())) {
            if (!temporary) {
                group.setTokenExpiryDateTime(Timestamp.valueOf(endOfCentury));
            } else if (expiryDateTime != null) {
                group.setTokenExpiryDateTime(Timestamp.valueOf(expiryDateTime));
            }
            tokenToReturn = group.getGroupTokenCode();
            logMessage = temporary ? String.format("Changed group joining code closing time to %s", expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                    : "Extended group joining code to permanent";
        } else {
            logger.info("Generating a group join code that is open ...");
            tokenToReturn = String.valueOf(tokenGeneratorService.getNextToken());
            LocalDateTime expiry = (temporary) ? expiryDateTime : endOfCentury;
            group.setTokenExpiryDateTime(Timestamp.valueOf(expiry));
            group.setGroupTokenCode(tokenToReturn);
            logMessage = temporary ? String.format("Created join code, %s, with closing time %s", tokenToReturn, expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                    : String.format("Created join code %s, to remain open until closed by group", tokenToReturn);
        }

        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(),
                                                                     GroupLogType.TOKEN_CHANGED, 0L, logMessage)));

        return tokenToReturn;
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
        group.setTokenExpiryDateTime(Timestamp.from(Instant.now()));

        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.TOKEN_CHANGED,
                                                                     0L, "Group join code closed")));
    }

    @Override
    @Transactional
    public void updateDiscoverable(String userUid, String groupUid, boolean discoverable, String authUserPhoneNumber) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        String logEntry;
        if (discoverable) {
            User authorizer = (authUserPhoneNumber == null) ? user : userRepository.findByPhoneNumber(authUserPhoneNumber);
            group.setDiscoverable(true);
            group.setJoinApprover(authorizer);
            logEntry = "Set group publicly discoverable, with join approver " + authorizer.nameToDisplay();
        } else {
            group.setJoinApprover(null);
            group.setDiscoverable(false);
            logEntry = "Set group hidden from public";
        }

        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.DISCOVERABLE_CHANGED,
                                                                     0L, logEntry)));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findPublicGroups(String searchTerm) {
        String modifiedSearchTerm = searchTerm.trim();
        // Group foundByToken = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(searchTerm, Timestamp.valueOf(LocalDateTime.now()));
        return groupRepository.findByGroupNameContainingIgnoreCaseAndDiscoverable(modifiedSearchTerm, true);
    }

    @Override
    public Group findGroupFromJoinCode(String joinCode) {
        Group groupToReturn = groupRepository.findByGroupTokenCode(joinCode);
        if (groupToReturn == null) return null;
        if (groupToReturn.getTokenExpiryDateTime().before(Timestamp.valueOf(LocalDateTime.now()))) return null;
        return groupToReturn;
    }

    @Override
    public Set<Group> subGroups(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        return new HashSet<>(groupRepository.findByParentAndActiveTrue(group));
    }

    @Override
    public List<Group> parentChain(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        List<Group> parentGroups = new ArrayList<Group>();
        recursiveParentGroups(group, parentGroups);
        return parentGroups;
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupTreeDTO> groupTree(String userUid) {
        User user = userRepository.findOneByUid(userUid);
        List<Object[]> listObjArray = groupRepository.getGroupMemberTree(user.getId());
        List<GroupTreeDTO> list = new ArrayList<>();
        for (Object[] objArray : listObjArray) {
            list.add(new GroupTreeDTO(objArray));
        }
        return list;
    }

    // todo: make sure this isn't too expensive ... the checking function might be
    @Override
    @Transactional(readOnly = true)
    public Set<Group> possibleParents(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group groupToMakeChild = groupRepository.findOneByUid(groupUid);

        Set<Group> groupsWithPermission = permissionBroker.getActiveGroups(user,Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
        groupsWithPermission.remove(groupToMakeChild);

        return groupsWithPermission.stream().filter(g -> !isGroupAlsoParent(groupToMakeChild, g)).collect(Collectors.toSet());
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

        Set<GroupLog> logs = new HashSet<>();
        logs.add(new GroupLog(parent.getId(), user.getId(), GroupLogType.SUBGROUP_ADDED, child.getId(),
                              "Subgroup added"));
        logs.add(new GroupLog(child.getId(), user.getId(), GroupLogType.PARENT_CHANGED, parent.getId(),
                              "Parent group added or changed"));
        logGroupEventsAfterCommit(logs);
    }

    @Override
    public Set<Group> mergeCandidates(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // todo: may want to check for both update and add members ...
        Set<Group> otherGroups = permissionBroker.getActiveGroups(user, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
        otherGroups.remove(group);
        return otherGroups;
    }

    // if this returns true, then the group being passed as child is already in the parent chain of the desired
    // parent, which will create an infinite loop, hence prevent it
    private boolean isGroupAlsoParent(Group possibleChildGroup, Group possibleParentGroup) {
        for (Group g : parentChain(possibleParentGroup.getUid())) {
            if (g.getId() == possibleChildGroup.getId()) return true;
        }
        return false;
    }

    // todo: watch & verify this method
    private void recursiveParentGroups(Group childGroup, List<Group> parentGroups) {
        parentGroups.add(childGroup);
        if (childGroup.getParent() != null && childGroup.getParent().getId() != 0) {
            recursiveParentGroups(childGroup.getParent(),parentGroups);
        }
    }

    @Override
    @Transactional(readOnly = true)
    public LocalDateTime getLastTimeGroupActiveOrModified(String groupUid) {
        LocalDateTime lastActive = getLastTimeGroupActive(groupUid);
        LocalDateTime lastModified = getLastTimeGroupModified(groupUid);
        return (lastActive != null && lastActive.isAfter(lastModified)) ? lastActive : lastModified;
    }

    @Override
    public GroupLog getMostRecentLog(Group group) {
        return groupLogRepository.findFirstByGroupIdOrderByCreatedDateTimeDesc(group.getId());
    }

    private LocalDateTime getLastTimeGroupActive(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        Event latestEvent = eventRepository.findTopByAppliesToGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        return (latestEvent != null) ? latestEvent.getEventDateTimeAtSAST() :
                group.getCreatedDateTime().toLocalDateTime();
    }

    private LocalDateTime getLastTimeGroupModified(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        // todo: change groupLog to use localdatetime
        GroupLog latestGroupLog = groupLogRepository.findFirstByGroupIdOrderByCreatedDateTimeDesc(group.getId());
        return (latestGroupLog != null) ? LocalDateTime.ofInstant(latestGroupLog.getCreatedDateTime(), getSAST()) :
                group.getCreatedDateTime().toLocalDateTime();
    }

    @Override
    public List<LocalDate> getMonthsGroupActive(String groupUid) {
        // todo: make this somewhat more sophisticated, including checking for active/inactive months, paid months etc
        Group group = groupRepository.findOneByUid(groupUid);
        LocalDate groupStartDate = group.getCreatedDateTime().toLocalDateTime().toLocalDate();
        LocalDate today = LocalDate.now();
        LocalDate monthIterator = LocalDate.of(groupStartDate.getYear(), groupStartDate.getMonth(), 1);
        List<LocalDate> months = new ArrayList<>();
        while (monthIterator.isBefore(today)) {
            months.add(monthIterator);
            monthIterator = monthIterator.plusMonths(1L);
        }
        return months;
    }

    @Override
    public List<GroupLog> getLogsForGroup(Group group, LocalDateTime periodStart, LocalDateTime periodEnd) {
        Sort sort = new Sort(Sort.Direction.ASC, "CreatedDateTime");
        return groupLogRepository.findByGroupIdAndCreatedDateTimeBetween(group.getId(), Timestamp.valueOf(periodStart),
                                                                         Timestamp.valueOf(periodEnd), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> retrieveGroupEvents(Group group, EventType eventType, LocalDateTime periodStart, LocalDateTime periodEnd) {
        List<Event> events;
        Sort sort = new Sort(Sort.Direction.ASC, "EventStartDateTime");
        Instant beginning, end;
        if (periodStart == null && periodEnd == null) {
            beginning = group.getCreatedDateTime().toInstant();
            end = convertToSystemTime(getVeryLongTimeAway(), getSAST());
        } else if (periodStart == null) { // since first condition is false, means period end is not null
            beginning = group.getCreatedDateTime().toInstant();
            end = convertToSystemTime(periodEnd, getSAST());
        } else if (periodEnd == null) { // since first & second conditions false, means period start is not null
            beginning = convertToSystemTime(periodStart, getSAST());
            end = getVeryLongTimeAway().toInstant(ZoneOffset.UTC);
        } else {
            beginning = convertToSystemTime(periodStart, getSAST());
            end = convertToSystemTime(periodEnd, getSAST());
        }

        if (eventType == null) {
            events = eventRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, beginning, end, sort);
        } else if (eventType.equals(EventType.MEETING)) {
            events = (List) meetingRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, beginning, end);
        } else if (eventType.equals(EventType.VOTE)) {
            events = (List) voteRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, beginning, end);
        } else {
            events = eventRepository.findByAppliesToGroupAndEventStartDateTimeBetween(group, beginning, end, sort);
        }

        return events;
    }

}
