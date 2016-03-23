package za.org.grassroot.services;

import com.google.common.collect.Sets;
import edu.emory.mathcs.backport.java.util.Collections;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.repository.GroupRepository;
import za.org.grassroot.core.repository.UserRepository;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class GroupBrokerImpl implements GroupBroker {

    private final Logger logger = LoggerFactory.getLogger(GroupBrokerImpl.class);

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PermissionBroker permissionBroker;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private AsyncGroupEventLogger asyncGroupEventLogger;

    @Override
    @Transactional(readOnly = true)
    public Group load(String groupUid) {
        return groupRepository.findOneByUid(groupUid);
    }

    @Override
    @Transactional
    public Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos, GroupPermissionTemplate groupPermissionTemplate) {

        Objects.requireNonNull(userUid);
        Objects.requireNonNull(name);
        Objects.requireNonNull(membershipInfos);
        Objects.requireNonNull(groupPermissionTemplate);

        User user = userRepository.findOneByUid(userUid);

        Group parent = null;
        if (parentGroupUid != null) {
            parent = groupRepository.findOneByUid(parentGroupUid);
        }

        logger.info("Creating new group: name={}, membershipInfos={}, groupPermissionTemplate={},  parent={}, user={}",
                name, membershipInfos, groupPermissionTemplate, parent, user);
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

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        group.setGroupName(name);

        Set<GroupLog> groupLogs = Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_RENAMED, group.getId(), "Group renamed to " + group.getGroupName()));
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

        Set<Membership> memberships = new HashSet<>();
        for (MembershipInfo membershipInfo : membershipInfos) {
            User user = existingUserMap.getOrDefault(membershipInfo.getPhoneNumberWithCCode(), new User(membershipInfo.getPhoneNumberWithCCode(), membershipInfo.getDisplayName()));
            String roleName = membershipInfo.getRoleName();
            Membership membership = roleName == null ? group.addMember(user) : group.addMember(user, roleName);
            if (membership != null) {
                memberships.add(membership);
            }
        }

        return memberships;
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
            resultGroup = create(user.getUid(), newGroupName, null, membershipInfos, GroupPermissionTemplate.DEFAULT_GROUP);
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

        return resultGroup;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findPublicGroups(String searchTerm) {
        String modifiedSearchTerm = searchTerm.trim();
        // Group foundByToken = groupRepository.findByGroupTokenCodeAndTokenExpiryDateTimeAfter(searchTerm, Timestamp.valueOf(LocalDateTime.now()));
        return groupRepository.findByGroupNameContainingIgnoreCaseAndDiscoverable(searchTerm, true);
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

        group.setReminderMinutes(reminderMinutes);
        String logMessage = String.format("Changed reminder default to %d minutes", reminderMinutes);
        logGroupEventsAfterCommit(Collections.singleton(new GroupLog(group.getId(), user.getId(),
                                                                     GroupLogType.REMINDER_DEFAULT_CHANGED, 0L, logMessage)));
    }
}
