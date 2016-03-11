package za.org.grassroot.services;

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

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class GroupBrokerImpl implements GroupBroker {

    private final Logger logger = LoggerFactory.getLogger(GroupBrokerImpl.class);

    @Autowired
    private GroupRepository groupRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PermissionsManagementService permissionsManagementService;
    @Autowired
    private GroupAccessControlManagementService groupAccessControlManagementService;
    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;
    @Autowired
    private AsyncGroupEventLogger asyncGroupEventLogger;

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

        Group group = new Group(name, user);
        GroupLog groupAddedEventLog;
        if (parent == null) {
            groupAddedEventLog = new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_ADDED, null);
        } else {
            group.setParent(parent);
            groupAddedEventLog = new GroupLog(parent.getId(), user.getId(), GroupLogType.GROUP_ADDED, group.getId());
        }

        Set<Membership> memberships = addMembers(user, group, membershipInfos);

        permissionsManagementService.setRolePermissionsFromTemplate(group, groupPermissionTemplate);
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
        // we want to log group events after transaction has committed
        AfterTxCommitTask afterTxCommitTask = () -> asyncGroupEventLogger.logGroupEvents(groupLogs);
        applicationEventPublisher.publishEvent(afterTxCommitTask);
    }

    // Just trying out the impact of straightforward manual ACL check

    private void validateGroupPermission(User user, Group targetGroup, Permission requiredPermission) {
        if (!isGroupPermissionAvailable(user, targetGroup, requiredPermission)) {
            throw new RuntimeException("User " + user + " has no permission " + requiredPermission + " available for group " + targetGroup);
        }
    }

    private boolean isGroupPermissionAvailable(User user, Group group, Permission requiredPermission) {
        for (Membership membership : user.getMemberships()) {
            if (membership.getGroup().equals(group)) {
                return membership.getRole().getPermissions().contains(requiredPermission);
            }
        }
        return false;
    }

    @Override
    @Transactional
    public void deactivate(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!isDeactivationAvailable(user, group)) {
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
    public boolean isDeactivationAvailable(User user, Group group) {
        // todo: Integrate with permission checking -- for now, just checking if group created by user in last 48 hours
        boolean isUserGroupCreator = group.getCreatedByUser().equals(user);
        Instant deactivationTimeThreshold = group.getCreatedDateTime().toInstant().plus(Duration.ofHours(48));
        return isUserGroupCreator && Instant.now().isBefore(deactivationTimeThreshold);
    }

    @Override
    @Transactional
    public void updateName(String userUid, String groupUid, String name) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setGroupName(name);

        Set<GroupLog> groupLogs = Collections.singleton(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_RENAMED, group.getId(), "Group renamed to " + group.getGroupName()));
        logGroupEventsAfterCommit(groupLogs);
    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);

        logger.info("Adding members: group={}, memberships={}, user={}", group, membershipInfos, user);
        Set<Membership> memberships = addMembers(user, group, membershipInfos);

        Set<GroupLog> groupLogs = new HashSet<>();
        for (Membership membership : memberships) {
            groupLogs.add(new GroupLog(group.getId(), user.getId(), GroupLogType.GROUP_MEMBER_ADDED, membership.getUser().getId()));
        }
        logGroupEventsAfterCommit(groupLogs);
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
    public void updateMembershipRole(String userUid, String groupUid, String memberUid, String roleName) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        Membership membership = group.getMemberships().stream()
                .filter(membership1 -> membership1.getUser().getUid().equals(memberUid))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There is no member under UID " + memberUid + " in group " + group));

        logger.info("Updating membership role: membership={}, roleName={}, user={}", membership, roleName, user);

        Role role = group.getRole(roleName);
        membership.setRole(role);
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
                deactivate(user.getUid(), groupInto.getUid());
                deactivate(user.getUid(), groupFrom.getUid());
            }
        } else {
            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupFrom.getMemberships());
            Set<Membership> memberships = addMembers(user, groupInto, membershipInfos);
            resultGroup = groupInto;
            if (!leaveActive) {
                deactivate(user.getUid(), groupFrom.getUid());
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
}
