package za.org.grassroot.services;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import za.org.grassroot.core.domain.*;
import za.org.grassroot.core.domain.geo.GroupLocation;
import za.org.grassroot.core.domain.notification.EventInfoNotification;
import za.org.grassroot.core.dto.GroupTreeDTO;
import za.org.grassroot.core.enums.EventLogType;
import za.org.grassroot.core.enums.EventType;
import za.org.grassroot.core.enums.GroupLogType;
import za.org.grassroot.core.enums.UserLogType;
import za.org.grassroot.core.repository.*;
import za.org.grassroot.services.enums.GroupPermissionTemplate;
import za.org.grassroot.services.exception.GroupDeactivationNotAvailableException;
import za.org.grassroot.services.geo.CenterCalculationResult;
import za.org.grassroot.services.geo.GeoLocationBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBroker;
import za.org.grassroot.services.util.LogsAndNotificationsBundle;
import za.org.grassroot.services.util.TokenGeneratorService;

import java.sql.Timestamp;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static za.org.grassroot.core.enums.UserInterfaceType.UNKNOWN;
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
    private LogsAndNotificationsBroker logsAndNotificationsBroker;
    @Autowired
    private TokenGeneratorService tokenGeneratorService;
    @Autowired
    private MessageAssemblingService messageAssemblingService;
    @Autowired
    private GeoLocationBroker geoLocationBroker;
    @Autowired
    private GroupLocationRepository groupLocationRepository;
    @Autowired
    private EventManagementService eventManagementService;
    @Autowired
    private EventLogRepository eventLogRepository;

    @Override
    @Transactional(readOnly = true)
    public Group load(String groupUid) {
        return groupRepository.findOneByUid(groupUid);
    }

    @Override
    @PreAuthorize("hasRole('ROLE_SYSTEM_ADMIN')")
    @Transactional(readOnly = true)
    public List<Group> loadAll() {
        return groupRepository.findAll();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> searchUsersGroups(String userUid, String searchTerm) {
	    Objects.requireNonNull(userUid);
	    Objects.requireNonNull(searchTerm);

	    if (searchTerm.trim().isEmpty()) {
		    throw new IllegalArgumentException("Error, cannot search for blank term");
	    }

	    User user = userRepository.findOneByUid(userUid);
        return groupRepository.findByMembershipsUserAndGroupNameContainingIgnoreCaseAndActiveTrue(user, searchTerm);
    }


    @Override
    @Transactional
    public Group create(String userUid, String name, String parentGroupUid, Set<MembershipInfo> membershipInfos,
                        GroupPermissionTemplate groupPermissionTemplate, String description, Integer reminderMinutes, boolean openJoinToken) {

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

        LogsAndNotificationsBundle bundle = addMemberships(user, group, membershipInfos, true);

        bundle.addLog(new GroupLog(group, user, GroupLogType.GROUP_ADDED, null));
        if (parent != null) {
            bundle.addLog(new GroupLog(parent, user, GroupLogType.SUBGROUP_ADDED, group.getId(), "Subgroup added"));
        }

        permissionBroker.setRolePermissionsFromTemplate(group, groupPermissionTemplate);
        group = groupRepository.save(group);

        logger.info("Group created under UID {}", group.getUid());

        if (openJoinToken) {
            JoinTokenOpeningResult joinTokenOpeningResult = openJoinTokenInternal(user, group, null);
            bundle.addLog(joinTokenOpeningResult.getGroupLog());
        }

        logsAndNotificationsBroker.storeBundle(bundle);

        return group;
    }

    private void logActionLogsAfterCommit(Set<ActionLog> actionLogs) {
        if (!actionLogs.isEmpty()) {
            LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();
            bundle.addLogs(actionLogs);

            // we want to log group events after transaction has committed
            AfterTxCommitTask afterTxCommitTask = () -> logsAndNotificationsBroker.asyncStoreBundle(bundle);
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

        Set<ActionLog> actionLogs = new HashSet<>();
        actionLogs.add(new GroupLog(group, user, GroupLogType.GROUP_REMOVED, null));
        if (group.getParent() != null) {
            actionLogs.add(new GroupLog(group.getParent(), user, GroupLogType.SUBGROUP_REMOVED, group.getId()));
        }

        logActionLogsAfterCommit(actionLogs);
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

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_RENAMED, group.getId(), "Group renamed to " + group.getGroupName());
        logActionLogsAfterCommit(Collections.singleton(groupLog));
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
        GroupLog groupLog = new GroupLog(group, user, GroupLogType.DESCRIPTION_CHANGED, group.getId(),
                "Group description changed to " + group.getDescription());
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional
    public void addMembers(String userUid, String groupUid, Set<MembershipInfo> membershipInfos, boolean adminUserCalling) {
        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        if (!adminUserCalling) {
            permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_ADD_GROUP_MEMBER);
        } else {
            // todo : insert a check on admin user permission here
        }

        logger.info("Adding members: group={}, memberships={}, user={}", group, membershipInfos, user);
        LogsAndNotificationsBundle bundle = addMemberships(user, group, membershipInfos, false);

        logsAndNotificationsBroker.storeBundle(bundle);
    }

    @Override
    @Transactional
    public void addMemberViaJoinCode(String userUidToAdd, String groupUid, String tokenPassed) {
        User user = userRepository.findOneByUid(userUidToAdd);
        Group group = groupRepository.findOneByUid(groupUid);
        if (!tokenPassed.equals(group.getGroupTokenCode()) || Instant.now().isAfter(group.getTokenExpiryDateTime().toInstant()))
            throw new RuntimeException("Invalid token: " + tokenPassed); // todo: create a custom version

        logger.info("Adding a member via token code: group={}, user={}, code={}", group, user, tokenPassed);
        group.addMember(user, BaseRoles.ROLE_ORDINARY_MEMBER);
        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_MEMBER_ADDED_VIA_JOIN_CODE, user.getId(),
                                    "Member joined via join code: " + tokenPassed);
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

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
                        .map(m -> m.getUser())
                        .collect(Collectors.toSet());

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

    private LogsAndNotificationsBundle addMemberships(User initiator, Group group, Set<MembershipInfo> membershipInfos, boolean duringGroupCreation) {
        // note: User objects should only ever store phone numbers in the msisdn format (i.e, with country code at front, no '+')
        Set<String> memberPhoneNumbers = membershipInfos.stream().map(MembershipInfo::getPhoneNumberWithCCode).collect(Collectors.toSet());
        logger.info("phoneNumbers returned: ...." + memberPhoneNumbers);
        Set<User> existingUsers = new HashSet<>(userRepository.findByPhoneNumberIn(memberPhoneNumbers));
        Map<String, User> existingUserMap = existingUsers.stream().collect(Collectors.toMap(User::getPhoneNumber, user -> user));
        logger.info("Number of existing users ... " + existingUsers.size());

        Set<User> createdUsers = new HashSet<>();
        Set<Membership> memberships = new HashSet<>();

        for (MembershipInfo membershipInfo : membershipInfos) {
            // note: splitting this instead of getOrDefault, since that method calls default method even if it finds something, hence spurious user creation
            String phoneNumberWithCCode = membershipInfo.getPhoneNumberWithCCode();
            User user = existingUserMap.get(phoneNumberWithCCode);
            if (user == null) {
                logger.info("Adding a new user, via group creation, with phone number ... " + phoneNumberWithCCode);
                user = new User(phoneNumberWithCCode, membershipInfo.getDisplayName());
                createdUsers.add(user);
            }
            String roleName = membershipInfo.getRoleName();
            Membership membership = roleName == null ? group.addMember(user) : group.addMember(user, roleName);
            if (membership != null) {
                memberships.add(membership);
            }
        }

        // make sure the newly created users are stored
        userRepository.save(createdUsers);
        userRepository.flush();

        // adding action logs and event notifications ...
        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        for (User createdUser : createdUsers) {
            bundle.addLog(new UserLog(createdUser.getUid(), UserLogType.CREATED_IN_DB, String.format("Created by being added to group with ID: %s", group.getUid()), UNKNOWN));
        }

        Set<Meeting> meetings = (Set) group.getUpcomingEventsIncludingParents(event -> event.getEventType().equals(EventType.MEETING));

        final GroupLogType logType = duringGroupCreation ? GroupLogType.GROUP_MEMBER_ADDED_AT_CREATION : GroupLogType.GROUP_MEMBER_ADDED;
        for (Membership membership : memberships) {
            User member = membership.getUser();

            GroupLog groupLog = new GroupLog(group, initiator, logType, member.getId());
            bundle.addLog(groupLog);

            // for each meeting that belongs to this group, or it belongs to one of parent groups and apply to subgroups,
            // we create event notification for new member, but in case when meeting belongs to parent group, then only if member
            // is not already contained in this ancestor group (otherwise, it already got the notification for such meetings)
            for (Meeting meeting : meetings) {
                Group meetingGroup = meeting.getAncestorGroup();
                if (meetingGroup.equals(group) || !meetingGroup.hasMember(member)) {
                    // meeting doesn't have to always apply to every member of its group ...
                    boolean appliesToMember = meeting.isAllGroupMembersAssigned() || meeting.getAssignedMembers().contains(member);
                    if (appliesToMember) {
                        String message = messageAssemblingService.createEventInfoMessage(member, meeting);
                        bundle.addNotification(new EventInfoNotification(member, message, groupLog, meeting));
                    }
                }
            }
        }

        return bundle;
    }

    private Set<ActionLog> removeMemberships(User initiator, Group group, Set<Membership> memberships) {
        Set<ActionLog> actionLogs = new HashSet<>();
        for (Membership membership : memberships) {
            group.removeMembership(membership);
            actionLogs.add(new GroupLog(group, initiator, GroupLogType.GROUP_MEMBER_REMOVED, membership.getUser().getId()));
        }
        return actionLogs;
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

        Set<ActionLog> actionLogs = removeMemberships(user, group, Collections.singleton(membership));
        logActionLogsAfterCommit(actionLogs);
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
                User savedUser = userRepository.findByPhoneNumber(m.getPhoneNumber());
                Membership savedMembership = group.getMembership(savedUser);
                if (savedMembership.getRole().getName() != m.getRoleName())
                    updateMembershipRole(userUid, groupUid, savedUser.getUid(), m.getRoleName());
            } else {
                // note: could also just do this with a removeAll, but since we're running the contain check, may as well
                membersToAdd.add(m);
            }
        }

        LogsAndNotificationsBundle bundle = new LogsAndNotificationsBundle();

        if (!membershipsToRemove.isEmpty()) {
            // note: only call if non-empty to avoid throwing no permission error if user hasn't removed anyone
            Set<ActionLog> removeMembershipsLogs = removeMemberships(user, group, membershipsToRemove);
            bundle.addLogs(removeMembershipsLogs);
        }

        if (!membersToAdd.isEmpty()) {
            // note: as above, only call if non-empty so permission check only happens
            LogsAndNotificationsBundle addMembershipsBundle = addMemberships(user, group, membersToAdd, false);
            bundle.addBundle(addMembershipsBundle);
        }

        logsAndNotificationsBroker.storeBundle(bundle);
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
            resultGroup = create(user.getUid(), newGroupName, null, membershipInfos, GroupPermissionTemplate.DEFAULT_GROUP, null, null, false);
            if (!leaveActive) {
                deactivate(user.getUid(), groupInto.getUid(), false);
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }
        } else {

            Set<MembershipInfo> membershipInfos = MembershipInfo.createFromMembers(groupFrom.getMemberships());
            LogsAndNotificationsBundle bundle = addMemberships(user, groupInto, membershipInfos, false);
            resultGroup = groupInto;
            if (!leaveActive) {
                deactivate(user.getUid(), groupFrom.getUid(), false);
            }

            logsAndNotificationsBroker.storeBundle(bundle);
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
        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user, GroupLogType.PERMISSIONS_CHANGED, 0L,
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
        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user,
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

        logActionLogsAfterCommit(Collections.singleton(new GroupLog(group, user, GroupLogType.LANGUAGE_CHANGED,
                                                                     0L, String.format("Set default language to %s", newLocale))));

    }

    @Override
    @Transactional
    public String openJoinToken(String userUid, String groupUid, LocalDateTime expiryDateTime) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        JoinTokenOpeningResult result = openJoinTokenInternal(user, group, expiryDateTime);

        logActionLogsAfterCommit(Collections.singleton(result.getGroupLog()));

        return result.getToken();
    }

    private JoinTokenOpeningResult openJoinTokenInternal(User user, Group group, LocalDateTime expiryDateTime) {
        LocalDateTime currentExpiry = (group.getTokenExpiryDateTime() != null) ? group.getTokenExpiryDateTime().toLocalDateTime() : null;
        final LocalDateTime endOfCentury = getVeryLongTimeAway();

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        logger.info("Opening join token: user={}, group={}, expiryDateTime={}", user, group, expiryDateTime);

        boolean temporary = expiryDateTime != null;

        // if there is already a valid token we are just changing expiry and returning it
        String token, logMessage;
        if (currentExpiry != null && currentExpiry.isAfter(LocalDateTime.now())) {
            if (!temporary) {
                group.setTokenExpiryDateTime(Timestamp.valueOf(endOfCentury));
            } else if (expiryDateTime != null) {
                group.setTokenExpiryDateTime(Timestamp.valueOf(expiryDateTime));
            }
            token = group.getGroupTokenCode();
            logMessage = temporary ? String.format("Changed group joining code closing time to %s", expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME))
                    : "Extended group joining code to permanent";
        } else {
            logger.info("Generating a group join code that is open ...");
            token = tokenGeneratorService.getNextToken();
            LocalDateTime expiry = (temporary) ? expiryDateTime : endOfCentury;
            group.setTokenExpiryDateTime(Timestamp.valueOf(expiry));
            group.setGroupTokenCode(token);
            logMessage = temporary ?
                    String.format("Created join code, %s, with closing time %s", token, expiryDateTime.format(DateTimeFormatter.ISO_DATE_TIME)) :
                    String.format("Created join code %s, to remain open until closed by group", token);
        }

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.TOKEN_CHANGED, 0L, logMessage);

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
        group.setTokenExpiryDateTime(Timestamp.from(Instant.now()));

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.TOKEN_CHANGED, 0L, "Group join code closed");
        logActionLogsAfterCommit(Collections.singleton(groupLog));
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

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.DISCOVERABLE_CHANGED, 0L, logEntry);
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    @Transactional(readOnly = true)
    public List<Group> findPublicGroups(String searchTerm, String searchingUserUid) {
        Objects.requireNonNull(searchingUserUid);
        User searchUser = userRepository.findOneByUid(searchingUserUid);
        String modifiedSearchTerm = searchTerm.trim();
        logger.info("running search : term = {}, user = {}", modifiedSearchTerm, searchUser);

        // note : would likely be more efficient to do this filter in query, but can't get named query to do exclusion
        // on contains quite right, so ... come back as part of general search implementation
        List<Group> allGroups = groupRepository.findByGroupNameContainingIgnoreCaseAndDiscoverable(modifiedSearchTerm, true);
        return allGroups.stream()
                .filter(group -> !group.hasMember(searchUser))
                .collect(Collectors.toList());
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

        Set<Group> groupsWithPermission = permissionBroker.getActiveGroupsWithPermission(user,Permission.GROUP_PERMISSION_CREATE_SUBGROUP);
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

        Set<ActionLog> actionLogs = new HashSet<>();
        actionLogs.add(new GroupLog(parent, user, GroupLogType.SUBGROUP_ADDED, child.getId(), "Subgroup added"));
        actionLogs.add(new GroupLog(child, user, GroupLogType.PARENT_CHANGED, parent.getId(), "Parent group added or changed"));
        logActionLogsAfterCommit(actionLogs);
    }

    @Override
    public Set<Group> mergeCandidates(String userUid, String groupUid) {
        Objects.requireNonNull(userUid);
        Objects.requireNonNull(groupUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);

        permissionBroker.validateGroupPermission(user, group, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);

        // todo: may want to check for both update and add members ...
        Set<Group> otherGroups = permissionBroker.getActiveGroupsWithPermission(user, Permission.GROUP_PERMISSION_UPDATE_GROUP_DETAILS);
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
    @Transactional(readOnly = true)
    public GroupLog getMostRecentLog(Group group) {
        return groupLogRepository.findFirstByGroupOrderByCreatedDateTimeDesc(group);
    }

    private LocalDateTime getLastTimeGroupActive(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        Event latestEvent = eventRepository.findTopByParentGroupAndEventStartDateTimeNotNullOrderByEventStartDateTimeDesc(group);
        return (latestEvent != null) ? latestEvent.getEventDateTimeAtSAST() :
                group.getCreatedDateTime().toLocalDateTime();
    }

    private LocalDateTime getLastTimeGroupModified(String groupUid) {
        Group group = groupRepository.findOneByUid(groupUid);
        // todo: change groupLog to use localdatetime
        GroupLog latestGroupLog = groupLogRepository.findFirstByGroupOrderByCreatedDateTimeDesc(group);
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
        return groupLogRepository.findByGroupAndCreatedDateTimeBetween(group, convertToSystemTime(periodStart, getSAST()),
                                                                         convertToSystemTime(periodEnd, getSAST()), sort);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Event> retrieveGroupEvents(Group group, EventType eventType, Instant periodStart, Instant periodEnd) {
        List<Event> events;
        Sort sort = new Sort(Sort.Direction.ASC, "EventStartDateTime");
        Instant beginning, end;
        if (periodStart == null && periodEnd == null) {
            beginning = group.getCreatedDateTime().toInstant();
            end = convertToSystemTime(getVeryLongTimeAway(), getSAST());
        } else if (periodStart == null) { // since first condition is false, means period end is not null
            beginning = group.getCreatedDateTime().toInstant();
            end = periodEnd;
        } else if (periodEnd == null) { // since first & second conditions false, means period start is not null
            beginning = periodStart;
            end = getVeryLongTimeAway().toInstant(ZoneOffset.UTC);
        } else {
            beginning = periodStart;
            end = periodEnd;
        }

        if (eventType == null) {
            events = eventRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end, sort);
        } else if (eventType.equals(EventType.MEETING)) {
            events = (List) meetingRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end);
        } else if (eventType.equals(EventType.VOTE)) {
            events = (List) voteRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end);
        } else {
            events = eventRepository.findByParentGroupAndEventStartDateTimeBetweenAndCanceledFalse(group, beginning, end, sort);
        }

        return events;
    }

    @Override
    @Transactional
    public void calculateGroupLocation(String groupUid, LocalDate localDate) {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(localDate);

        Group group = groupRepository.findOneByUid(groupUid);

        // delete so we can recalculate
        groupLocationRepository.deleteByGroupAndLocalDate(group, localDate);

        Set<String> memberUids = group.getMembers().stream().map(User::getUid).collect(Collectors.toSet());
        CenterCalculationResult result = geoLocationBroker.calculateCenter(memberUids, localDate);
        if (result.isDefined()) {
            // for now, score is simply ratio of found member locations to total member count
            float score = result.getUserCount() / (float) memberUids.size();
            GroupLocation groupLocation = new GroupLocation(group, localDate, result.getCenter(), score);
            groupLocationRepository.save(groupLocation);
        } else {
            logger.debug("No member location data found for group {} for local date {}", group, localDate);
        }
    }

    @Override
    public void saveGroupImage(String userUid, String groupUid, String imageUrl, byte[] image) {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(imageUrl);
        Objects.requireNonNull(image);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setImage(image);
        group.setImageUrl(imageUrl);

        groupRepository.save(group);

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_AVATAR_UPLOADED, group.getId(),
                "Group avatar uploaded");
        logActionLogsAfterCommit(Collections.singleton(groupLog));

    }

    @Override
    public void removeGroupImage(String userUid, String groupUid)  {
        Objects.requireNonNull(groupUid);
        Objects.requireNonNull(userUid);

        User user = userRepository.findOneByUid(userUid);
        Group group = groupRepository.findOneByUid(groupUid);
        group.setImage(null);
        group.setImageUrl(null);

        groupRepository.save(group);

        GroupLog groupLog = new GroupLog(group, user, GroupLogType.GROUP_AVATAR_REMOVED, group.getId(),
                "Group avatar removed");
        logActionLogsAfterCommit(Collections.singleton(groupLog));
    }

    @Override
    public Group getGroupByImageUrl(String imageUrl) {
        return groupRepository.findOneByImageUrl(imageUrl);
    }

    @Override
	@Transactional(readOnly = true)
	public List<Group> fetchGroupsWithOneCharNames(User user, int sizeThreshold) {
        //for now limiting this to only groups created by the user
		List<Group> candidateGroups = new ArrayList<>(groupRepository.findActiveGroupsWithNamesLessThanOneCharacter(user));
		return candidateGroups.stream()
				.filter(group -> group.getMembers().size() <= sizeThreshold)
				.sorted(Collections.reverseOrder())
				.collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ChangedSinceData<Group> getActiveGroups(User user, Instant changedSince) {
        Objects.requireNonNull(user, "User cannot be null");

        List<Group> activeGroups = groupRepository.findByMembershipsUserAndActiveTrue(user);

        // here we put all those groups that have been satisfying query above, but not anymore since 'changedSince' moment
        Set<String> removedUids = new HashSet<>();
        if (changedSince != null) {
            List<Group> deactivatedAfter = groupRepository.findMemberGroupsDeactivatedAfter(user, changedSince);
            List<Group> formerMembersGroups = groupRepository.findMembershipRemovedAfter(user.getId(), changedSince);
            removedUids = Stream.concat(deactivatedAfter.stream(), formerMembersGroups.stream())
                    .map(Group::getUid)
                    .collect(Collectors.toSet());
        }

        List<Group> groups = activeGroups.stream()
                .filter(group -> changedSince == null || isGroupChangedSince(group, changedSince))
                .collect(Collectors.toList());

        return new ChangedSinceData<>(groups, removedUids);
    }

    private boolean isGroupChangedSince(Group group, Instant changedSince) {
        GroupLog mostRecentLog = getMostRecentLog(group);
        if (mostRecentLog.getCreatedDateTime().isAfter(changedSince)) {
            return true;
        }

        Event mostRecentEvent = eventManagementService.getMostRecentEvent(group);
        if (mostRecentEvent != null) {
            if (mostRecentEvent.getCreatedDateTime().isAfter(changedSince)) {
				return true;
			}

            // if most recent event is created before last time user checked this group, then we check if this event has been changed after this last time
            EventLog lastChangeEventLog = eventLogRepository.findFirstByEventAndEventLogTypeOrderByCreatedDateTimeDesc(mostRecentEvent, EventLogType.CHANGE);
            if (lastChangeEventLog != null && lastChangeEventLog.getCreatedDateTime().isAfter(changedSince)) {
				return true;
			}
        }

        return false;
    }
}
